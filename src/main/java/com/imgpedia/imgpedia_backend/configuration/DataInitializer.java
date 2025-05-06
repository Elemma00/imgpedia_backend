package com.imgpedia.imgpedia_backend.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;

import com.imgpedia.imgpedia_backend.models.auth.Role;
import com.imgpedia.imgpedia_backend.repository.RoleRepository;

/**
 * * DataInitializer is a CommandLineRunner that initializes the database with default roles.
*/
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        if (roleRepository.findByName("ADMIN").isEmpty()) {
            roleRepository.save(new Role("ADMIN"));
        }
        
        if (roleRepository.findByName("USER").isEmpty()) {
            roleRepository.save(new Role("USER"));
        }
    }
}
