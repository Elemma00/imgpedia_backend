package com.imgpedia.imgpedia_backend.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.imgpedia.imgpedia_backend.configuration.jwt.JwtFilterConfigurer;
import com.imgpedia.imgpedia_backend.configuration.jwt.JwtTokenProvider;
import com.imgpedia.imgpedia_backend.services.UserService;


/**
 * SecurityConfig is a configuration class that sets up security for the application.
 * It configures HTTP security, authentication provider, and JWT token provider.
 * This class is annotated with @Configuration and @EnableWebSecurity, indicating that it contains Spring security configuration.
 */

@Configuration
@EnableWebSecurity
public class SecurityConfig {   

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Configures the security filter chain for the application.
     * It sets up CSRF protection, session management, and authorization rules for different endpoints.
     * @param http the HttpSecurity object to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // <-- Permitir preflight
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/sparql/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/data/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/data/*").authenticated()
            .requestMatchers("/api/admin/**").hasAnyRole("SUPERADMIN","ADMIN")
            .anyRequest().permitAll()
        )
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .with(new JwtFilterConfigurer(jwtTokenProvider), jwt -> {})
        .authenticationProvider(authenticationProvider())
        .build();
}

/**
 * Configures CORS for the application to allow cross-origin requests.
 * @return the configured CorsConfigurationSource
 */
@Bean
public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
    org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
    configuration.setAllowedOrigins(java.util.Arrays.asList("http://localhost:4200"));
    configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(java.util.Arrays.asList("authorization", "content-type", "x-auth-token"));
    configuration.setExposedHeaders(java.util.Arrays.asList("x-auth-token"));
    configuration.setAllowCredentials(true);
    org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
    /**
     * Configures the authentication provider for the application.
     * It uses DaoAuthenticationProvider with a custom UserDetailsService and PasswordEncoder.
     * @return the configured DaoAuthenticationProvider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(userService);
        return provider;
    }
    
    /**
     * Configures the authentication manager for the application.
     * It uses the AuthenticationConfiguration to create an AuthenticationManager bean.
     * @param config the AuthenticationConfiguration object to use
     * @return the configured AuthenticationManager
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
