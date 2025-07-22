package com.imgpedia.imgpedia_backend.configuration;

import java.util.Arrays;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.imgpedia.imgpedia_backend.configuration.jwt.JwtFilterConfigurer;
import com.imgpedia.imgpedia_backend.configuration.jwt.JwtTokenProvider;
import com.imgpedia.imgpedia_backend.services.UserService;

/**
 * Configures security settings for the application, including authentication, authorization, CORS, and JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public SecurityConfig(UserService userService, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Configures the security filter chain, including CSRF, session management, authorization, CORS, and JWT.
     *
     * @param http HttpSecurity instance to configure.
     * @return Configured SecurityFilterChain.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> configureAuthorization(auth))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .with(new JwtFilterConfigurer(jwtTokenProvider), jwt -> {})
            .authenticationProvider(authenticationProvider());
        return http.build();
    }

    /**
     * Configures authorization rules for different endpoints.
     *
     * @param auth the AuthorizeHttpRequestsConfigurer to configure.
     */
    private void configureAuthorization(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/api/auth/login").permitAll()
            .requestMatchers("/api/auth/register").authenticated()
            .requestMatchers("/api/sparql/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/data/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/data/*").authenticated()
            .requestMatchers("/api/admin/**").hasAnyRole("SUPERADMIN", "ADMIN")
            .anyRequest().permitAll();
    }

    /**
     * Configures CORS to allow cross-origin requests from specific origins.
     *
     * @return Configured CorsConfigurationSource.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200", "https://imgpedia.dcc.uchile.cl"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(Arrays.asList("x-auth-token"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the authentication provider using a custom UserDetailsService and PasswordEncoder.
     *
     * @return Configured DaoAuthenticationProvider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(userService);
        return provider;
    }

    /**
     * Provides the AuthenticationManager bean using the given AuthenticationConfiguration.
     *
     * @param config AuthenticationConfiguration instance.
     * @return Configured AuthenticationManager.
     * @throws Exception if configuration fails.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
