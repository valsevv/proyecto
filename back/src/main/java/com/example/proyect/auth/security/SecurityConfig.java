package com.example.proyect.auth.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
            .cors(Customizer.withDefaults())        // <-- usa tu CorsConfigurationSource
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            // Asegura el orden: CORS corre antes automáticamente
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();

    // Incluí todos los orígenes que realmente usás
    cfg.setAllowedOrigins(List.of(
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173"
        // "https://tu-frontend.com" // para prod
    ));

    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of(
        "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"
    ));
    cfg.setExposedHeaders(List.of("Authorization", "Location"));

    // Si necesitás enviar cookies/cabeceras con credenciales cruzadas:
    // - NO podés usar "*" en AllowedOrigins.
    // - Asegurate que el frontend setee credentials: 'include' si las usa.
    cfg.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
}
}


// @Configuration
// public class FilterConfig {

//     @Bean
//     public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter(
//             JwtAuthenticationFilter filter) {

//         FilterRegistrationBean<JwtAuthenticationFilter> registration =
//                 new FilterRegistrationBean<>();

//         registration.setFilter(filter);
//         registration.addUrlPatterns("/api/*");
//         registration.setOrder(1);

//         return registration;
//     }
// }
