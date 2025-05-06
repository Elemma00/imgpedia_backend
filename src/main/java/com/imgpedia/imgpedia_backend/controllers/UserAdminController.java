package com.imgpedia.imgpedia_backend.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.controllers.interfaces.UserAdmin;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.services.UserService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController implements UserAdmin {
    
    @Autowired
    private UserService userService;

    @Override
    public ResponseEntity<?> createUser(Map<String, String> userData) {
      try {
            String username = userData.get("username");
            String password = userData.get("password");
            String email = userData.get("email");
            String role = userData.getOrDefault("role", "USER");
            
            if (username == null || password == null || email == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, password and email are required"));
            }
            
            User newUser = userService.createUser(username, password, email);
            
            if (!"USER".equals(role)) {
                userService.addRoleToUser(username, role);
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "User created successfully",
                "username", newUser.getUsername()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<?> addRoleToUser(Map<String, String> roleData) {
        try {
            String username = roleData.get("username");
            String roleName = roleData.get("role");
            
            if (username == null || roleName == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and role are required"));
            }
            
            userService.addRoleToUser(username, roleName);
            
            return ResponseEntity.ok(Map.of(
                "message", "Role added successfully",
                "username", username,
                "role", roleName
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<?> listUsers() {
        try {
            List<Map<String, Object>> users = userService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @Override
    public ResponseEntity<?> changePassword(String username, Map<String, String> passwordData) {
        try {
            String newPassword = passwordData.get("password");
            
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "A password is required"));
            }
            
            userService.changePassword(username, newPassword);
            
            return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully",
                "username", username
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<?> deleteUser(String username) {
        try {
            if (username.equals("admin")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "This user cannot be deleted"));
            }
            
            userService.deleteUser(username);
            
            return ResponseEntity.ok(Map.of(
                "message", "User deleted successfully",
                "username", username
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<?> updateUserStatus(String username, Map<String, Boolean> statusData) {
        try {
            Boolean enabled = statusData.get("enabled");
            
            if (enabled == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Enabled status is required"));
            }
            
            userService.updateUserStatus(username, enabled);
            
            String action = enabled ? "Enable" : "Disable";
            
            return ResponseEntity.ok(Map.of(
                "message", "User " + action + " successfully",
                "username", username,
                "enabled", enabled
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
