package com.example.book_store.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Logs one line per request: method, path, status and duration; level follows the status (5xx ERROR, 4xx WARN). */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // before Spring Security, so requests it rejects (401/403) are logged too
class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500; // stays 500 if the chain throws before a status is written
        try {
            chain.doFilter(req, res);
            status = res.getStatus();
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
            // path only, no query string: it may carry tokens or personal data
            log.atLevel(level).log("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), status, ms);
        }
    }
}
