package com.imgpedia.imgpedia_backend.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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
 * This class implements UserDetailsService to load user-specific data.
 */
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
    
    @Transactional
    public User createUser(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("The username is already in use");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email is already in use");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        
        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> {
                Role newRole = new Role("USER");
                return roleRepository.save(newRole);
            });
        
        user.addRole(userRole);
        
        return userRepository.save(user);
    }
    
    @Transactional
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Transactional
    public void addRoleToUser(String username, String roleName) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Role role = roleRepository.findByName(roleName)
            .orElseGet(() -> {
                Role newRole = new Role(roleName);
                return roleRepository.save(newRole);
            });
        
        user.addRole(role);
        userRepository.save(user);
    }

    @Transactional
    public User changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
            .map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("email", user.getEmail());
                userInfo.put("enabled", user.isEnabled());
                userInfo.put("roles", user.getRoles().stream()
                    .map(role -> role.getName())
                    .collect(Collectors.toList()));
                return userInfo;
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        userRepository.delete(user);
    }

    @Transactional
    public User updateUserStatus(String username, boolean enabled) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        if (!enabled) {
            boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));
            
            if (isAdmin) {
                throw new RuntimeException("Cannot disable this user.");
            }
        }
        
        user.setEnabled(enabled);
        return userRepository.save(user);
    }
}
