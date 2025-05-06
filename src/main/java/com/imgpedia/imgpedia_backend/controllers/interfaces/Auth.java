package com.imgpedia.imgpedia_backend.controllers.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


import com.imgpedia.imgpedia_backend.models.auth.AuthRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Auth is an interface that defines the authentication API for the application.
 * It contains methods for user login and other authentication-related operations.
 */
@Tag(name = "Authentication API", description = "API for authentication")
public interface Auth {

    /**
     * Authenticates a user with the provided credentials.
     * @param loginRequest The authentication request containing username and password.
     * @return A ResponseEntity containing the authentication response or an error message.
     */
    @Operation(summary = "Login user") 
    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody AuthRequest loginRequest);
      
}
