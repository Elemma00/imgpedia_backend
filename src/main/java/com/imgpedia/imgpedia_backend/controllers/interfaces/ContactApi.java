package com.imgpedia.imgpedia_backend.controllers.interfaces;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * ContactApi is an interface that defines the API for managing contact information.
 */
@Tag(name = "Contact API", description = "API for Contact")
public interface ContactApi {

    @Operation(summary= "Send contact email")
    @PostMapping("/email")
    ResponseEntity<?> sendContactEmail(@RequestBody Map<String, String> payload);
}
