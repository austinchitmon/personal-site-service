package com.chitmon.backend.security;

import com.chitmon.backend.users.UserSyncFilter;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final JwtDecoder jwtDecoder;
    private final UserSyncFilter userSyncFilter;

    public SecurityConfig(JwtDecoder jwtDecoder, UserSyncFilter userSyncFilter) {
        this.jwtDecoder = jwtDecoder;
        this.userSyncFilter = userSyncFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: no session cookie exists for CSRF to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Must come first: a CORS preflight OPTIONS carries no Authorization
                        // header. This filter chain runs before MVC's CorsConfig, so without
                        // this, preflight to an authenticated path would be rejected here and
                        // the browser would never send the real request.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/me", "/users/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(adminClaimsConverter())))
                // Runs after bearer-token authentication succeeds, so it has a Jwt to read.
                .addFilterAfter(userSyncFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private JwtAuthenticationConverter adminClaimsConverter() {
        var converter = new JwtAuthenticationConverter();
        var scopes = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            // Only server-managed metadata is trusted. user_metadata is user-editable.
            if (jwt.getClaim("app_metadata") instanceof Map<?, ?> metadata
                    && "admin".equals(metadata.get("role"))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return authorities;
        });
        return converter;
    }
}
