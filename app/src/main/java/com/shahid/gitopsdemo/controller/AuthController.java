package com.shahid.gitopsdemo.controller;

import com.shahid.gitopsdemo.model.LoginRequest;
import com.shahid.gitopsdemo.model.LoginResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dummy authentication for demo purposes only. Credentials are sourced from
 * the Kubernetes Secret (DEMO_USERNAME / DEMO_PASSWORD env vars) so the
 * GitOps repo can rotate them without touching application code.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final String demoUsername;
    private final String demoPassword;

    public AuthController(@Value("${app.auth.username}") String demoUsername,
                           @Value("${app.auth.password}") String demoPassword) {
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user='{}'", request.getUsername());

        boolean authenticated = demoUsername.equals(request.getUsername())
                && demoPassword.equals(request.getPassword());

        if (authenticated) {
            log.info("Login succeeded for user='{}'", request.getUsername());
            return ResponseEntity.ok(new LoginResponse(true, "Login successful", request.getUsername()));
        }

        log.warn("Login failed for user='{}'", request.getUsername());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse(false, "Invalid username or password", null));
    }
}
