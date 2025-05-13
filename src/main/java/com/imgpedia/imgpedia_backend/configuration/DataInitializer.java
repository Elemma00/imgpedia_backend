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
 * * DataInitializer is a CommandLineRunner that initializes the database with default roles.
*/
@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Value("${admin}")
    private String superAdminUser;
    
    @Value("${password}")
    private String superAdminPass;

    @Override
    public void run(String... args) throws Exception {
        ImgpediaLogger.info("Initializing database with default roles and super admin user");

        Role superAdminRole = createRoleIfNotExists("SUPERADMIN");
        Role adminRole = createRoleIfNotExists("ADMIN");
        Role userRole = createRoleIfNotExists("USER");
        
 
        if (userRepository.findByUsername(superAdminUser).isEmpty()) {
            User superadmin = new User();
            superadmin.setUsername(superAdminUser);
            superadmin.setPassword(passwordEncoder.encode(superAdminPass));
            superadmin.setEmail("admin@imgpedia.org");
            superadmin.setRoles(Collections.singleton(superAdminRole));
            superadmin.setEnabled(true);
            
            userRepository.save(superadmin);
            ImgpediaLogger.info("SuperAdmin user created successfully");
        } else {
            ImgpediaLogger.info("SuperAdmin user already exists");
        }
    }
    
    private Role createRoleIfNotExists(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role(roleName);
                    return roleRepository.save(newRole);
                });
    }
}
