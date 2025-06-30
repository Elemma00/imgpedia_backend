package com.imgpedia.imgpedia_backend.controllers;

import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.services.RdfUploadService;
import com.imgpedia.imgpedia_backend.services.UserService;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


class RdfUploaderControllerTest {

    @InjectMocks
    private RdfUploaderController controller;

    @Mock
    private RdfUploadService rdfUploadService;

    @Mock
    private UserService userService;

    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set up a mock authenticated user in the security context
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("testuser", "password");
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock para evitar NullPointerException
        when(rdfUploadService.getUserService()).thenReturn(userService);
    }

    @Test
    void uploadRdfData_shouldReturnForbidden_whenUserIsNull() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "test.ttl", "text/turtle", "data".getBytes());

        ResponseEntity<?> response = controller.uploadRdfData(file);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("disabled"));
    }

    @Test
    void uploadRdfData_shouldReturnForbidden_whenUserIsDisabled() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "test.ttl", "text/turtle", "data".getBytes());

        ResponseEntity<?> response = controller.uploadRdfData(file);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("disabled"));
    }

    @Test
    void uploadRdfData_shouldReturnBadRequest_whenFileIsEmpty() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
        .thenReturn(Optional.of(user));

        when(user.isEnabled()).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "test.ttl", "text/turtle", new byte[0]);

        ResponseEntity<?> response = controller.uploadRdfData(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MessagesLogs.UPLOAD_FILE_EMPTY, response.getBody());
    }

    @Test
    void uploadRdfData_shouldReturnBadRequest_whenFileExtensionIsInvalid() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        ResponseEntity<?> response = controller.uploadRdfData(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED, response.getBody());
    }

    @Test
    void uploadRdfData_shouldReturnAccepted_whenValidFile() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(true);
        doNothing().when(rdfUploadService).initializeUpload(anyString(), anyString());
        MockMultipartFile file = new MockMultipartFile("file", "test.ttl", "text/turtle", "data".getBytes());

        ResponseEntity<?> response = controller.uploadRdfData(file);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("processing", body.get("status"));
        assertTrue(body.containsKey("uploadId"));
    }

    @Test
    void uploadMultipleRdfData_shouldReturnForbidden_whenUserIsNull() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.empty());

        MockMultipartFile[] files = {new MockMultipartFile("file", "test.ttl", "text/turtle", "data".getBytes())};
        ResponseEntity<?> response = controller.uploadMultipleRdfData(files);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void uploadMultipleRdfData_shouldReturnBadRequest_whenFilesAreEmpty() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(true);

        ResponseEntity<?> response = controller.uploadMultipleRdfData(new MockMultipartFile[0]);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MessagesLogs.UPLOAD_FILE_EMPTY, response.getBody());
    }

    @Test
    void uploadMultipleRdfData_shouldProcessFilesCorrectly() {
        when(rdfUploadService.getUserService().findByUsername(anyString()))
                .thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(true);
        doNothing().when(rdfUploadService).initializeUpload(anyString(), anyString());

        MockMultipartFile validFile = new MockMultipartFile("file", "test.ttl", "text/turtle", "data".getBytes());
        MockMultipartFile invalidFile = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.ttl", "text/turtle", new byte[0]);
        MockMultipartFile[] files = {validFile, invalidFile, emptyFile};

        ResponseEntity<?> response = controller.uploadMultipleRdfData(files);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(3, body.get("count"));
        List<?> uploads = (List<?>) body.get("uploads");
        assertEquals(3, uploads.size());
    }

    @Test
    void getUploadStatus_shouldReturnNotFound_whenStatusIsNotFound() {
        when(rdfUploadService.getUploadStatusById("id1"))
                .thenReturn(Map.of("status", "not_found"));

        ResponseEntity<?> response = controller.getUploadStatus("id1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("not found"));
    }

    @Test
    void getUploadStatus_shouldReturnOk_whenStatusExists() {
        Map<String, Object> status = Map.of("status", "processing");
        when(rdfUploadService.getUploadStatusById("id2")).thenReturn(status);

        ResponseEntity<?> response = controller.getUploadStatus("id2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(status, response.getBody());
    }

    @Test
    void getBatchUploadStatus_shouldReturnBadRequest_whenNoIdsProvided() {
        ResponseEntity<?> response = controller.getBatchUploadStatus("   ");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getBatchUploadStatus_shouldReturnStatuses() {
        when(rdfUploadService.getUploadStatusById("id1")).thenReturn(Map.of("status", "done"));
        when(rdfUploadService.getUploadStatusById("id2")).thenReturn(Map.of("status", "processing"));

        ResponseEntity<?> response = controller.getBatchUploadStatus("id1, id2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(2, body.get("count"));
        Map<?, ?> statuses = (Map<?, ?>) body.get("statuses");
        assertEquals("done", ((Map<?, ?>) statuses.get("id1")).get("status"));
        assertEquals("processing", ((Map<?, ?>) statuses.get("id2")).get("status"));
    }

    @Test
    void getAllUploadStatuses_shouldReturnOk() {
        Map<String, Object> allStatuses = Map.of("status", "done");
        when(rdfUploadService.getAllUploadStatuses()).thenReturn(allStatuses);
        ResponseEntity<?> response = controller.getAllUploadStatuses();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(allStatuses, response.getBody());
    }
}

