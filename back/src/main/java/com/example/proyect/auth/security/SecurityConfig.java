package com.example.proyect.auth.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Preflight siempre permitido
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                
                // Public page routes
                .requestMatchers("/", "/login").permitAll()
                
                // Block direct access to HTML files in static directory
                .requestMatchers(request -> {
                    String path = request.getRequestURI();
                    return path.startsWith("/screen/") && path.endsWith(".html");
                }).denyAll()
                
                // Public static assets (CSS, JS, images, libraries)
                // Allow everything else under /screen/ and other static resources
                .requestMatchers(
                    "/assets/**",
                    "/styles/**", 
                    "/screen/**",
                    "/lib/**",
                    "/gameobjects/**",
                    "/scenes/**",
                    "/network/**",
                    "/shared/**",
                    "/ui/**",
                    "/utils/**",
                    "/favicon.ico",
                    "/game.js"
                ).permitAll()
                
                // Protected page routes - require authentication
                .requestMatchers(
                    "/menu",
                    "/credits",
                    "/lobby-browser", 
                    "/lobby-waiting",
                    "/game"
                ).authenticated()
                
                // All API endpoints require authentication (except /api/auth/**)
                .anyRequest().authenticated()
            )
            // Manejo centralizado de 401/403
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    // Check if it's an HTML request
                    String accept = req.getHeader("Accept");
                    if (accept != null && accept.contains("text/html")) {
                        // Redirect HTML requests to login page
                        res.sendRedirect("/login");
                    } else {
                        // Return JSON for API requests
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"error\":\"Unauthorized\"}");
                    }
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Forbidden\"}");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.2*.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*",
            "https://*.ngrok-free.dev"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"
        ));
        cfg.setExposedHeaders(List.of("Authorization", "Location", "Set-Cookie"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}