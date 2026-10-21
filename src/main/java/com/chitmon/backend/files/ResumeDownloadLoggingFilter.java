package com.chitmon.backend.files;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ResumeDownloadLoggingFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTRIBUTE = ResumeDownloadLoggingFilter.class.getName() + ".requestId";
    private static final Logger log = LoggerFactory.getLogger(ResumeDownloadLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(request.getContextPath() + "/files/resume.pdf")
                || !(request.getMethod().equals("GET") || request.getMethod().equals("HEAD"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            // Includes failures writing the body after the controller has returned.
            log.error("Resume response delivery failed requestId={} method={} status={}",
                    requestId, request.getMethod(), response.getStatus(), ex);
            throw ex;
        }
    }
}
