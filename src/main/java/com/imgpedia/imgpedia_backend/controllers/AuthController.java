package com.imgpedia.imgpedia_backend.controllers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.configuration.jwt.JwtTokenProvider;
import com.imgpedia.imgpedia_backend.controllers.interfaces.Auth;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.auth.AuthRequest;
import com.imgpedia.imgpedia_backend.models.auth.AuthResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController implements Auth {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public ResponseEntity<?> login(AuthRequest loginRequest) {
      try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            String token = jwtTokenProvider.createToken(loginRequest.getUsername(), roles);
            ImgpediaLogger.info("User " + loginRequest.getUsername() + " logged in successfully");
            return ResponseEntity.ok(new AuthResponse(token, loginRequest.getUsername()));
        } catch (Exception e) {
            ImgpediaLogger.error("Authentication failed for user " + loginRequest.getUsername() + ": " + e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

}
