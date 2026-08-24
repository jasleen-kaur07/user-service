package com.ecommerce.userservice.security;

import com.ecommerce.userservice.client.AuthServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectMapper objectMapper,
                                                   AuthServiceClient authServiceClient) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()

                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/merchants/**").permitAll()

                        .requestMatchers("/api/users/**").authenticated()

                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(RestAuthEntryPoints.unauthorized(objectMapper))
                        .accessDeniedHandler(RestAuthEntryPoints.forbidden(objectMapper)))
                .addFilterBefore(new IdentityHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new TokenAuthenticationFilter(authServiceClient, objectMapper),
                        IdentityHeaderFilter.class)
                .build();
    }
}
