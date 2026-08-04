package com.chitmon.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    // Built from the JWKS URI directly rather than JwtDecoders.fromIssuerLocation(issuerUri):
    // that helper requires the OIDC discovery document at
    // <issuer>/.well-known/openid-configuration, which is part of Supabase's separate
    // "OAuth Server" feature and 404s unless that feature is explicitly enabled. The plain
    // JWKS endpoint works regardless, so issuer/audience are validated manually instead.
    // jwsAlgorithm(ES256) is required: withJwkSetUri(...) otherwise only accepts RS256 by
    // default and silently rejects Supabase's ES256-signed tokens (this project uses an
    // ECC P-256 signing key, not the legacy HS256/RSA schemes).
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${supabase.jwks-uri}") String jwksUri,
            @Value("${supabase.issuer-uri}") String issuerUri,
            @Value("${supabase.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new SupabaseAudienceValidator(audience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static class SupabaseAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;

        SupabaseAudienceValidator(String audience) {
            this.audience = audience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token.getAudience() != null && token.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience '" + audience + "' is missing", null));
        }
    }
}
