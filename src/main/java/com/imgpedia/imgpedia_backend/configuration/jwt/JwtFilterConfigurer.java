package com.imgpedia.imgpedia_backend.configuration.jwt;

import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


/**
 * Configures the JWT filter for Spring Security.
 * This class is responsible for adding the JWT filter to the security chain.
 */
public class JwtFilterConfigurer extends  SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity>{
    private final JwtTokenProvider jwtTokenProvider;

    public JwtFilterConfigurer(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        JwtTokenFilter customFilter = new JwtTokenFilter(jwtTokenProvider);
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
