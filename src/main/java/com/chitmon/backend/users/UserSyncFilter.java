package com.chitmon.backend.users;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps core.users in sync just-in-time: whenever a request carries a
 * validated Supabase JWT (whether or not the route itself requires auth), the
 * mirrored row is upserted from the token's claims.
 */
@Component
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserSyncService userSyncService;

    public UserSyncFilter(UserSyncService userSyncService) {
        this.userSyncService = userSyncService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            userSyncService.sync(auth.getToken());
        }
        chain.doFilter(request, response);
    }
}
