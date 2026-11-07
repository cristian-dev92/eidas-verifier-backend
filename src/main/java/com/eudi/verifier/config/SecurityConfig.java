package com.eudi.verifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // Deshabilitar CSRF para APIs REST
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Sin estado (JWT/Token based)
                .authorizeHttpRequests(auth -> auth
                        // Permitir acceso público a los endpoints de verificación y Swagger
                        .requestMatchers(
                                "/api/v1/verify/**",
                                "/api/v1/issuer/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Cualquier otra petición requerirá autenticación en el futuro (ej. panel de administración)
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
