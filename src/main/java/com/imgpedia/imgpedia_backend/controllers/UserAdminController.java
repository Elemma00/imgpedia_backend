package com.imgpedia.imgpedia_backend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.controllers.interfaces.UserAdmin;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.services.UserService;

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
                ImgpediaLogger.error("Username, password and email are required");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, password and email are required"));
            }
            
            User newUser = userService.createUser(username, password, email);
            
            if (!"USER".equals(role)) {
                userService.addRoleToUser(username, role);
            }
            ImgpediaLogger.info("User created: " + username);
            return ResponseEntity.ok(Map.of(
                "message", "User created successfully",
                "username", newUser.getUsername()
            ));
        } catch (Exception e) {
            ImgpediaLogger.error("Error creating user: " + e.getMessage());
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
                ImgpediaLogger.error("Username and role are required");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and role are required"));
            }

            // Solo el SUPERADMIN puede cambiar roles
            boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SUPERADMIN"));
            if (!isSuperAdmin) {
                ImgpediaLogger.error("Only SUPERADMIN can change user roles");
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Only SUPERADMIN can change user roles"));
            }

            userService.addRoleToUser(username, roleName);

            ImgpediaLogger.info("Role " + roleName + " added to user: " + username);
            return ResponseEntity.ok(Map.of(
                "message", "Role added successfully",
                "username", username,
                "role", roleName
            ));
        } catch (Exception e) {
            ImgpediaLogger.error("Error adding role to user: " + e.getMessage());
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
            ImgpediaLogger.error("Error listing users: " + e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    @Override
    public ResponseEntity<?> changePassword(String username, Map<String, String> passwordData) {
        try {
            String authenticatedUser = SecurityContextHolder
                    .getContext().getAuthentication().getName();

            if (!authenticatedUser.equals(username)) {
                ImgpediaLogger.error("User " + authenticatedUser + " tried to change password for " + username);
                return ResponseEntity.status(403)
                    .body(Map.of("error", "You can only change your own password"));
            }

            String newPassword = passwordData.get("password");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                ImgpediaLogger.error("A password is required");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "A password is required"));
            }

            userService.changePassword(username, newPassword);
            ImgpediaLogger.info("Password changed for user: " + username);
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
            String authenticatedUser = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SUPERADMIN"));
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            // No permitir borrar al superadmin
            if (username.equalsIgnoreCase("superadmin")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "This user cannot be deleted"));
            }

            // Solo el SUPERADMIN puede borrar administradores
            User userToDelete = userService.findByUsername(username).orElse(null);
            boolean isTargetAdmin = userToDelete.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ADMIN") || r.getName().equals("SUPERADMIN"));

            if (isTargetAdmin && !isSuperAdmin) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Only SUPERADMIN can delete ADMIN or SUPERADMIN users"));
            }

            // Los ADMIN solo pueden borrar usuarios con rol USER
            if (isAdmin && !isSuperAdmin) {
                boolean isTargetUser = userToDelete.getRoles().stream()
                    .anyMatch(r -> r.getName().equals("USER"));
                if (!isTargetUser) {
                    return ResponseEntity.status(403)
                        .body(Map.of("error", "ADMIN can only delete USER accounts"));
                }
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

            String authenticatedUser = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SUPERADMIN"));
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (username.equalsIgnoreCase("superadmin")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "This user cannot be disabled"));
            }

            User userToUpdate = userService.findByUsername(username).orElse(null);
            if (userToUpdate == null) {
                return ResponseEntity.status(404)
                    .body(Map.of("error", "User not found"));
            }

            boolean isTargetSuperAdmin = userToUpdate.getRoles().stream()
                .anyMatch(r -> r.getName().equals("SUPERADMIN"));
            boolean isTargetAdmin = userToUpdate.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ADMIN"));
            boolean isTargetUser = userToUpdate.getRoles().stream()
                .anyMatch(r -> r.getName().equals("USER"));

            // Nadie puede deshabilitar a un SUPERADMIN
            if (isTargetSuperAdmin) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "SUPERADMIN users cannot be disabled"));
            }

            // Solo SUPERADMIN puede deshabilitar ADMIN
            if (isTargetAdmin && !isSuperAdmin) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Only SUPERADMIN can disable ADMIN users"));
            }

            // ADMIN solo puede deshabilitar USER
            if (isAdmin && !isSuperAdmin) {
                if (!isTargetUser) {
                    return ResponseEntity.status(403)
                        .body(Map.of("error", "ADMIN can only disable USER accounts"));
                }
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
