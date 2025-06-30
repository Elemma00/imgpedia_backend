package com.imgpedia.imgpedia_backend.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.imgpedia.imgpedia_backend.configuration.jwt.JwtTokenProvider;
import com.imgpedia.imgpedia_backend.models.auth.AuthRequest;
import com.imgpedia.imgpedia_backend.models.auth.AuthResponse;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void login_successfulAuthentication_returnsAuthResponse() {
        String username = "testuser";
        String password = "testpass";
        String token = "jwt-token";
        List<String> roles = List.of("ROLE_USER");

        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        Authentication authentication = mock(Authentication.class);
        GrantedAuthority authority = () -> "ROLE_USER";
        when(authentication.getAuthorities()).thenReturn((Collection) Collections.singletonList(authority));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtTokenProvider.createToken(username, roles)).thenReturn(token);

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertTrue(response.getBody() instanceof AuthResponse);
        AuthResponse authResponse = (AuthResponse) response.getBody();
        assertEquals(token, authResponse.getToken());
        assertEquals(username, authResponse.getUsername());

        // Verify authenticationManager was called with correct credentials
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertEquals(username, captor.getValue().getPrincipal());
        assertEquals(password, captor.getValue().getCredentials());
    }

    @Test
    void login_failedAuthentication_returnsBadRequest() {
        String username = "baduser";
        String password = "badpass";
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Bad credentials"));

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(body.get("error").toString().contains("Authentication failed"));
    }
}