package com.imgpedia.imgpedia_backend.configuration;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.auth.Role;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.repository.RoleRepository;
import com.imgpedia.imgpedia_backend.repository.UserRepository;

/**
 * Initializes the database with default roles and a super admin user.
 * This class runs at application startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin}")
    private String superAdminUsername;

    @Value("${password}")
    private String superAdminPassword;

    @Autowired
    public DataInitializer(RoleRepository roleRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Executes the initialization logic at application startup.
     * Creates default roles and a super admin user if they do not exist.
     *
     * @param args command line arguments
     */
    @Override
    public void run(String... args) {
        ImgpediaLogger.info("Initializing database with default roles and super admin user");

        Role superAdminRole = ensureRoleExists("SUPERADMIN");
        ensureRoleExists("ADMIN");
        ensureRoleExists("USER");

        createSuperAdminIfNotExists(superAdminRole);
    }

    /**
     * Ensures a role with the given name exists in the database.
     * If it does not exist, it is created.
     *
     * @param roleName the name of the role
     * @return the existing or newly created Role
     */
    private Role ensureRoleExists(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    /**
     * Creates the super admin user if it does not already exist.
     *
     * @param superAdminRole the SUPERADMIN role to assign
     */
    private void createSuperAdminIfNotExists(Role superAdminRole) {
        if (userRepository.findByUsername(superAdminUsername).isEmpty()) {
            User superAdmin = new User();
            superAdmin.setUsername(superAdminUsername);
            superAdmin.setPassword(passwordEncoder.encode(superAdminPassword));
            superAdmin.setEmail("admin@imgpedia.org");
            superAdmin.setRoles(Collections.singleton(superAdminRole));
            superAdmin.setEnabled(true);

            userRepository.save(superAdmin);
            ImgpediaLogger.info("SuperAdmin user created successfully");
        } else {
            ImgpediaLogger.info("SuperAdmin user already exists");
        }
    }
}
