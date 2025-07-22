package com.imgpedia.imgpedia_backend.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.imgpedia.imgpedia_backend.models.auth.Role;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.repository.RoleRepository;
import com.imgpedia.imgpedia_backend.repository.UserRepository;

/**
 * Service class for managing user-related operations.
 * Implements UserDetailsService to integrate with Spring Security.
 */
@Service
public class UserService implements UserDetailsService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String RESERVED_USERNAME_SUPERADMIN = "superadmin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Loads a user by username for authentication.
     *
     * @param username the username to search for
     * @return UserDetails object
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Creates a new user with the USER role.
     *
     * @param username the username
     * @param password the password
     * @param email    the email
     * @return the created User
     */
    @Transactional
    public User createUser(String username, String password, String email) {
        validateUsernameAndEmail(username, email);
        User user = buildUser(username, password, email);
        Role userRole = getOrCreateRole(ROLE_USER);
        user.addRole(userRole);
        return userRepository.save(user);
    }

    /**
     * Finds a user by username.
     *
     * @param username the username
     * @return Optional containing the User if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Adds a role to a user.
     *
     * @param username the username
     * @param roleName the role to add
     */
    @Transactional
    public void addRoleToUser(String username, String roleName) {
        if (ROLE_SUPERADMIN.equalsIgnoreCase(roleName)) {
            throw new RuntimeException("Cannot assign SUPERADMIN role");
        }
        User user = getUserByUsername(username);
        Role role = getOrCreateRole(roleName);
        user.addRole(role);
        userRepository.save(user);
    }

    /**
     * Changes the password for a user.
     *
     * @param username    the username
     * @param newPassword the new password
     * @return the updated User
     */
    @Transactional
    public User changePassword(String username, String newPassword) {
        User user = getUserByUsername(username);
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /**
     * Retrieves all users with basic info and roles.
     *
     * @return list of user info maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
            .map(this::mapUserToInfo)
            .collect(Collectors.toList());
    }

    /**
     * Deletes a user by username.
     *
     * @param username the username
     */
    @Transactional
    public void deleteUser(String username) {
        User user = getUserByUsername(username);
        userRepository.delete(user);
    }

    /**
     * Updates the enabled status of a user, with role-based restrictions.
     *
     * @param username the username
     * @param enabled  the new enabled status
     * @return the updated User
     */
    @Transactional
    public User updateUserStatus(String username, boolean enabled) {
        User user = getUserByUsername(username);
        validateUserStatusChange(user);
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    /**
     * Checks if the current authenticated user has a specific role.
     *
     * @param roleName the role name
     * @return true if the user has the role
     */
    private boolean currentUserHasRole(String roleName) {
        return SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + roleName));
    }

    /**
     * Validates username and email for uniqueness and reserved names.
     */
    private void validateUsernameAndEmail(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("The username is already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email is already in use");
        }
        if (RESERVED_USERNAME_SUPERADMIN.equalsIgnoreCase(username)) {
            throw new RuntimeException("Cannot create user with reserved username 'superadmin'");
        }
    }

    /**
     * Builds a new User entity.
     */
    private User buildUser(String username, String password, String email) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        return user;
    }

    /**
     * Retrieves a user by username or throws an exception.
     */
    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    /**
     * Retrieves a role by name or creates it if not found.
     */
    private Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName)
            .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    /**
     * Maps a User entity to a user info map.
     */
    private Map<String, Object> mapUserToInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("enabled", user.isEnabled());
        userInfo.put("roles", user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toList()));
        return userInfo;
    }

    /**
     * Validates if the current user can change the status of the target user.
     */
    private void validateUserStatusChange(User user) {
        boolean isSuperAdmin = user.getRoles().stream()
            .anyMatch(role -> ROLE_SUPERADMIN.equals(role.getName()));
        if (isSuperAdmin) {
            throw new RuntimeException("SUPERADMIN users cannot be disabled");
        }

        boolean isTargetAdmin = user.getRoles().stream()
            .anyMatch(role -> ROLE_ADMIN.equals(role.getName()));
        boolean isTargetUser = user.getRoles().stream()
            .anyMatch(role -> ROLE_USER.equals(role.getName()));

        if (isTargetAdmin && !currentUserHasRole(ROLE_SUPERADMIN)) {
            throw new RuntimeException("Only SUPERADMIN can disable ADMIN users");
        }
        if (!isTargetAdmin && !isTargetUser) {
            throw new RuntimeException("Invalid target user role");
        }
        if (currentUserHasRole(ROLE_ADMIN) && !currentUserHasRole(ROLE_SUPERADMIN) && !isTargetUser) {
            throw new RuntimeException("ADMIN can only disable USER accounts");
        }
    }
}
