package com.example.proyect.auth.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }



@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {

    // Dejar pasar siempre preflight
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        filterChain.doFilter(request, response);
        return;
    }

    // Si ya existe autenticación, continuar
    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing != null && existing.isAuthenticated()) {
        filterChain.doFilter(request, response);
        return;
    }

    // Extract JWT from cookie
    String token = extractTokenFromCookie(request);
    
    // Fallback: also check Authorization header for backward compatibility during migration
    if (token == null) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
    }
    
    if (token != null && jwtService.isTokenValid(token)) {
        String username = jwtService.extractUsername(token);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                username,
                null,
                java.util.Collections.emptyList() // sin roles
            );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    
    // Siempre continuar con la cadena - SecurityConfig decidirá si se requiere auth
    filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("authToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

