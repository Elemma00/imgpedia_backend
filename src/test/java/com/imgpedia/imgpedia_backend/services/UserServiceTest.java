package com.imgpedia.imgpedia_backend.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.imgpedia.imgpedia_backend.models.auth.Role;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.repository.RoleRepository;
import com.imgpedia.imgpedia_backend.repository.UserRepository;


class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Clear SecurityContext before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void loadUserByUsername_UserExists_ReturnsUser() {
        User user = new User();
        user.setUsername("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertEquals(user, userService.loadUserByUsername("testuser"));
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("nouser")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername("nouser"));
    }

    @Test
    void createUser_Success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("email@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        Role userRole = new Role("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        User savedUser = new User();
        savedUser.setUsername("newuser");
        savedUser.setEmail("email@example.com");
        savedUser.setPassword("encoded");
        savedUser.addRole(userRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.createUser("newuser", "password", "email@example.com");
        assertEquals("newuser", result.getUsername());
        assertEquals("email@example.com", result.getEmail());
        assertTrue(result.getRoles().stream().anyMatch(r -> r.getName().equals("USER")));
    }

    @Test
    void createUser_UsernameExists_ThrowsException() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);
        assertThrows(RuntimeException.class, () -> userService.createUser("existing", "pass", "mail@mail.com"));
    }

    @Test
    void createUser_EmailExists_ThrowsException() {
        when(userRepository.existsByUsername("user")).thenReturn(false);
        when(userRepository.existsByEmail("mail@mail.com")).thenReturn(true);
        assertThrows(RuntimeException.class, () -> userService.createUser("user", "pass", "mail@mail.com"));
    }

    @Test
    void createUser_SuperadminUsername_ThrowsException() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(userRepository.existsByEmail("super@admin.com")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> userService.createUser("superadmin", "pass", "super@admin.com"));
    }

    @Test
    void findByUsername_ReturnsUser() {
        User user = new User();
        user.setUsername("findme");
        when(userRepository.findByUsername("findme")).thenReturn(Optional.of(user));
        Optional<User> result = userService.findByUsername("findme");
        assertTrue(result.isPresent());
        assertEquals("findme", result.get().getUsername());
    }

    @Test
    void addRoleToUser_Success() {
        User user = new User();
        user.setUsername("user");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        Role role = new Role("EDITOR");
        when(roleRepository.findByName("EDITOR")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.addRoleToUser("user", "EDITOR");
        assertTrue(user.getRoles().stream().anyMatch(r -> r.getName().equals("EDITOR")));
    }

    @Test
    void addRoleToUser_SuperadminRole_ThrowsException() {
        assertThrows(RuntimeException.class, () -> userService.addRoleToUser("user", "SUPERADMIN"));
    }

    @Test
    void addRoleToUser_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("nouser")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> userService.addRoleToUser("nouser", "EDITOR"));
    }

    @Test
    void changePassword_Success() {
        User user = new User();
        user.setUsername("user");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("encodedpass");
        when(userRepository.save(any(User.class))).thenReturn(user);

        User result = userService.changePassword("user", "newpass");
        assertEquals(user, result);
        assertEquals("encodedpass", user.getPassword());
    }

    @Test
    void changePassword_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("nouser")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> userService.changePassword("nouser", "pass"));
    }

    @Test
    void getAllUsers_ReturnsList() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user1");
        user1.setEmail("u1@mail.com");
        user1.setEnabled(true);
        user1.addRole(new Role("USER"));

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");
        user2.setEmail("u2@mail.com");
        user2.setEnabled(false);
        user2.addRole(new Role("ADMIN"));

        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2));

        List<Map<String, Object>> users = userService.getAllUsers();
        assertEquals(2, users.size());
        assertEquals("user1", users.get(0).get("username"));
        assertEquals("user2", users.get(1).get("username"));
    }

    @Test
    void deleteUser_Success() {
        User user = new User();
        user.setUsername("deluser");
        when(userRepository.findByUsername("deluser")).thenReturn(Optional.of(user));
        doNothing().when(userRepository).delete(user);

        userService.deleteUser("deluser");
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("nouser")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> userService.deleteUser("nouser"));
    }

    @Test
    void updateUserStatus_DisableUser_Success() {
        User user = new User();
        user.setUsername("user");
        user.setEnabled(true);
        user.addRole(new Role("USER"));
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Mock current user as ADMIN
        setAuthenticationWithRoles("ROLE_ADMIN");

        User result = userService.updateUserStatus("user", false);
        assertFalse(result.isEnabled());
    }

    @Test
    void updateUserStatus_DisableSuperadmin_ThrowsException() {
        User user = new User();
        user.setUsername("superadmin");
        user.setEnabled(true);
        user.addRole(new Role("SUPERADMIN"));
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));

        setAuthenticationWithRoles("ROLE_SUPERADMIN");

        assertThrows(RuntimeException.class, () -> userService.updateUserStatus("superadmin", false));
    }

    @Test
    void updateUserStatus_DisableAdminWithoutSuperadmin_ThrowsException() {
        User user = new User();
        user.setUsername("admin");
        user.setEnabled(true);
        user.addRole(new Role("ADMIN"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        setAuthenticationWithRoles("ROLE_ADMIN");

        assertThrows(RuntimeException.class, () -> userService.updateUserStatus("admin", false));
    }

    @Test
    void updateUserStatus_DisableAdminWithSuperadmin_Success() {
        User user = new User();
        user.setUsername("admin");
        user.setEnabled(true);
        user.addRole(new Role("ADMIN"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        setAuthenticationWithRoles("ROLE_SUPERADMIN");

        User result = userService.updateUserStatus("admin", false);
        assertFalse(result.isEnabled());
    }

    @Test
    void updateUserStatus_InvalidRole_ThrowsException() {
        User user = new User();
        user.setUsername("stranger");
        user.setEnabled(true);
        user.addRole(new Role("GUEST"));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(user));

        setAuthenticationWithRoles("ROLE_ADMIN");

        assertThrows(RuntimeException.class, () -> userService.updateUserStatus("stranger", false));
    }

    // Helper to mock authentication with roles
    private void setAuthenticationWithRoles(String... roles) {
        Authentication auth = mock(Authentication.class);
        Collection<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        // Cast necesario para evitar problemas de tipos genéricos con Mockito
        when(auth.getAuthorities()).thenReturn((Collection) authorities);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }
}