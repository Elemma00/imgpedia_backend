package com.imgpedia.imgpedia_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ImgpediaBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImgpediaBackendApplication.class, args);
    }
        
}
