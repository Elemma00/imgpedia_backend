package com.imgpedia.imgpedia_backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.controllers.interfaces.ContactApi;

@RestController
@RequestMapping("api/contact")
public class ContactController implements ContactApi{

    // @Autowired
    private JavaMailSender mailSender;

    @Override
    public ResponseEntity<?> sendContactEmail(Map<String, String> payload) {
        String name = payload.getOrDefault("name", "Sin nombre");
        String email = payload.getOrDefault("email", "Sin email");
        String message = payload.getOrDefault("message", "");
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo("faundez76@gmail.com"); // Cambia por el correo de destino real
            mailMessage.setSubject("Nuevo mensaje de contacto de IMGpedia");
            mailMessage.setText("Nombre: " + name + "\nEmail: " + email + "\nMensaje:\n" + message);

            mailSender.send(mailMessage);
            return ResponseEntity.ok(Map.of("message", "Correo enviado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo enviar el correo: " + e.getMessage()));
        }
    }

}
