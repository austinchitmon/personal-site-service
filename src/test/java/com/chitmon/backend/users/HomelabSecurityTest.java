package com.chitmon.backend.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chitmon.backend.config.CorsConfig;
import com.chitmon.backend.homelab.DatabaseHealthProbe;
import com.chitmon.backend.homelab.HomelabController;
import com.chitmon.backend.homelab.HomelabStatusService;
import com.chitmon.backend.security.JwtDecoderConfig;
import com.chitmon.backend.security.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class HomelabSecurityTest {
    private static final String ISSUER = "https://test.supabase.co/auth/v1";
    private static ECKey signingKey;
    private static HttpServer jwksServer;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static HomelabStatusService status;
    private static UserSyncService users;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, UserSyncFilter.class, HomelabController.class,
            ProbeController.class, CorsConfig.LocalCorsConfig.class})
    static class TestConfiguration {
        @Bean JwtDecoder jwtDecoder() {
            return new JwtDecoderConfig().jwtDecoder("http://127.0.0.1:" + jwksServer.getAddress().getPort()
                    + "/jwks", ISSUER, "authenticated");
        }
        @Bean UserSyncService users() { return mock(UserSyncService.class); }
        @Bean HomelabStatusService status() { return mock(HomelabStatusService.class); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/me") String me() { return "signed-in user"; }
        @GetMapping("/files/public-test") String publicFile() { return "public"; }
    }

    @BeforeAll
    static void start() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("monitor-test").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var output = exchange.getResponseBody()) { output.write(jwks); }
        });
        jwksServer.start();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("local");
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        status = context.getBean(HomelabStatusService.class);
        users = context.getBean(UserSyncService.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
        if (jwksServer != null) jwksServer.stop(0);
    }

    @BeforeEach
    void configure() {
        reset(status, users);
        when(status.status()).thenReturn(new HomelabStatusService.Status(Instant.now(),
                new HomelabStatusService.Api("up", 123), new DatabaseHealthProbe.Check("down", "Unavailable", 1L),
                new HomelabStatusService.Collector("unavailable", null, null, "No snapshot"), null));
    }

    private String token(Consumer<JWTClaimsSet.Builder> customise, ECKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated")
                .subject("00000000-0000-0000-0000-000000000001").expirationTime(Date.from(Instant.now().plusSeconds(180)))
                .claim("app_metadata", Map.of("role", "admin", "provider", "google"));
        customise.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("monitor-test").build(), claims.build());
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private String token(Consumer<JWTClaimsSet.Builder> customise) throws Exception {
        return token(customise, signingKey);
    }

    @Test
    void anonymousRequestsCannotReadMonitoringData() throws Exception {
        mvc.perform(get("/admin/homelab")).andExpect(status().isUnauthorized());
        verifyNoInteractions(status, users);
    }

    @Test
    void validGoogleAdminCanMonitorEvenWhenUserSyncAndDatabaseAreDown() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(users).sync(any());
        mvc.perform(get("/admin/homelab").header("Authorization", "Bearer " + token(builder -> {})))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.api.state").value("up"))
                .andExpect(jsonPath("$.database.state").value("down"));
        verifyNoInteractions(users);
    }

    @Test
    void signedInNonAdminIsDenied() throws Exception {
        mvc.perform(get("/admin/homelab").header("Authorization", "Bearer " + token(builder ->
                        builder.claim("app_metadata", Map.of("provider", "google")))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(status, users);
    }

    @Test
    void userEditableMetadataAndTopLevelRoleCannotGrantAdminAccess() throws Exception {
        mvc.perform(get("/admin/homelab").header("Authorization", "Bearer " + token(builder -> builder
                        .claim("app_metadata", Map.of()).claim("user_metadata", Map.of("role", "admin"))
                        .claim("role", "admin"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(status);
    }

    @Test
    void expiredWrongIssuerWrongAudienceAndForgedSignaturesAreRejected() throws Exception {
        String[] invalid = {
                token(builder -> builder.expirationTime(Date.from(Instant.now().minusSeconds(300)))),
                token(builder -> builder.issuer("https://another.supabase.co/auth/v1")),
                token(builder -> builder.audience("anon")),
                token(builder -> {}, new ECKeyGenerator(Curve.P_256).generate()),
                "not.a.jwt"
        };
        for (String token : invalid) {
            mvc.perform(get("/admin/homelab").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(status, users);
    }

    @Test
    void adminProtectionCoversTheWholeNamespace() throws Exception {
        mvc.perform(get("/admin/future-feature")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/future-feature").header("Authorization", "Bearer " + token(builder ->
                        builder.claim("app_metadata", Map.of())))).andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserSyncAndPublicEndpointsKeepTheirExistingBehaviour() throws Exception {
        mvc.perform(get("/me").header("Authorization", "Bearer " + token(builder ->
                builder.claim("app_metadata", Map.of())))).andExpect(status().isOk());
        verify(users).sync(any());
        mvc.perform(get("/files/public-test")).andExpect(status().isOk());
    }

    @Test
    void authenticatedBrowserPreflightDoesNotNeedABearerToken() throws Exception {
        mvc.perform(options("/admin/homelab").header("Origin", "http://127.0.0.1:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4200"));
        verifyNoInteractions(status, users);
    }
}
