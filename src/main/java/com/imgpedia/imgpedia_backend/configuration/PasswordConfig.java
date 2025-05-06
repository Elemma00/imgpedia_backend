package com.imgpedia.imgpedia_backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordConfig is a configuration class that provides a bean for password encoding.
 * It uses BCryptPasswordEncoder to encode passwords securely.
 * This class is annotated with @Configuration, indicating that it contains Spring configuration.
 * The passwordEncoder() method returns a new instance of BCryptPasswordEncoder.
 */
@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
