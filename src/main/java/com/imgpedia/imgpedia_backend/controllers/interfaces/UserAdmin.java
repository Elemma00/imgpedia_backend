package com.imgpedia.imgpedia_backend.controllers.interfaces;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * UserAdmin is an interface that defines the API for user administration.
 * It contains methods for creating users, adding roles, listing users, changing passwords,
 * deleting users, and updating user status.
 */
@Tag(name = "User Administration", description = "API for user administration")
public interface UserAdmin {

    /**
     * Creates a new user with the provided data.
     * @param userData The data for the new user.
     * @return A ResponseEntity containing the result of the user creation operation.
     */
    @Operation(summary = "Create a new user")
    @PostMapping("/create")
    ResponseEntity<?> createUser(@RequestBody Map<String, String> userData);

    /**
     * Adds a role to an existing user.
     * @param roleData The data for the role to be added.
     * @return A ResponseEntity containing the result of the role addition operation.
     */
    @Operation(summary = "Add a role to a user")
    @PostMapping("/role")
    ResponseEntity<?> addRoleToUser(@RequestBody Map<String, String> roleData);

    /**
     * Lists all users in the system.
     * @return A ResponseEntity containing the list of users.
     */
    @Operation(summary = "List all users")
    @GetMapping("/users")
    ResponseEntity<?> listUsers();
    
    /**
     * Change the password to a user.
     * @param username The username of the user to be retrieved.
     * @return A ResponseEntity containing the user details.
     */
    @Operation(summary = "Change user password")
    @PutMapping("/users/{username}/password")
    ResponseEntity<?> changePassword(
        @PathVariable String username, 
        @RequestBody Map<String, String> passwordData
    );
    
    /**
     * Deletes a user by username.
     * @param username The username of the user to be deleted.
     * @return A ResponseEntity containing the user details.
     */
    @Operation(summary = "Delete a user")
    @DeleteMapping("/users/{username}")
    ResponseEntity<?> deleteUser(@PathVariable String username);

    /**
     * Enable or disable a user by username.
     * @param username The username of the user to be enabled or disabled.
     * @param statusData The status data containing the new status.
     * @return A ResponseEntity containing the result of the operation.
     */
    @Operation(summary = "Enable or disable a user")
    @PatchMapping("/users/{username}/status")
    ResponseEntity<?> updateUserStatus(
        @PathVariable String username, 
        @RequestBody Map<String, Boolean> statusData
    );
}
