package com.imgpedia.imgpedia_backend.controllers;

import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.models.auth.Role;
import com.imgpedia.imgpedia_backend.services.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.http.HttpStatusCode;

class UserAdminControllerTest {

    @InjectMocks
    private UserAdminController controller;

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @Test
    void createUser_success() {
        Map<String, String> userData = Map.of(
                "username", "testuser",
                "password", "pass",
                "email", "test@example.com"
        );
        User user = new User();
        user.setUsername("testuser");
        when(userService.createUser("testuser", "pass", "test@example.com")).thenReturn(user);

        ResponseEntity<?> response = controller.createUser(userData);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("message").toString().contains("User created successfully"));
    }

    @Test
    void createUser_missingFields() {
        Map<String, String> userData = Map.of("username", "testuser");
        ResponseEntity<?> response = controller.createUser(userData);
        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("username, password and email are required"));
    }

    @Test
    void listUsers_success() {
        List<Map<String, Object>> users = List.of(Map.of("username", "testuser"));
        when(userService.getAllUsers()).thenReturn(users);

        ResponseEntity<?> response = controller.listUsers();

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(users, response.getBody());
    }

    @Test
    void changePassword_success() {
        when(authentication.getName()).thenReturn("testuser");
        Map<String, String> passwordData = Map.of("password", "newpass");

        ResponseEntity<?> response = controller.changePassword("testuser", passwordData);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        verify(userService).changePassword("testuser", "newpass");
    }

    @Test
    void changePassword_wrongUser() {
        when(authentication.getName()).thenReturn("otheruser");
        Map<String, String> passwordData = Map.of("password", "newpass");

        ResponseEntity<?> response = controller.changePassword("testuser", passwordData);

        assertEquals(HttpStatusCode.valueOf(403), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("You can only change your own password"));
    }

    @Test
    void changePassword_missingPassword() {
        when(authentication.getName()).thenReturn("testuser");
        Map<String, String> passwordData = Map.of();

        ResponseEntity<?> response = controller.changePassword("testuser", passwordData);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("A password is required"));
    }
    @Test
    void deleteUser_cannotDeleteSuperadmin() {
        ResponseEntity<?> response = controller.deleteUser("superadmin");
        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("This user cannot be deleted"));
    }

    @Test
    void updateUserStatus_userNotFound() {
        when(userService.findByUsername("nouser")).thenReturn(Optional.empty());
        Map<String, Boolean> statusData = Map.of("enabled", false);

        ResponseEntity<?> response = controller.updateUserStatus("nouser", statusData);

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("User not found"));
    }

    @Test
    void updateUserStatus_missingEnabled() {
        Map<String, Boolean> statusData = new HashMap<>();
        ResponseEntity<?> response = controller.updateUserStatus("testuser", statusData);
        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("Enabled status is required"));
    }
}