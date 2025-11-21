package com.example.salessavvy.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.salessavvy.dto.LoginRequest;
import com.example.salessavvy.entities.User;
import com.example.salessavvy.services.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/api/auth")
// Use a simple, non-trailing-slash origin here for consistency
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") 
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        try {
            User user = authService.authenticate(loginRequest.getUsername(), loginRequest.getPassword());
            String token = authService.generateToken(user);

            // --- CORRECT COOKIE SETTINGS FOR LOCAL HTTP DEVELOPMENT ---
            Cookie cookie = new Cookie("authToken", token);
            cookie.setHttpOnly(true);
            
            // Set to false for HTTP (localhost). Set to true ONLY for HTTPS/Production.
            cookie.setSecure(false); 
            
            cookie.setPath("/");
            cookie.setMaxAge(3600); // 1 hour

            // ⚠️ CRITICAL: Remove cookie.setDomain("localhost"); as it often causes issues 
            // with Postman and local testing environments.
            // cookie.setDomain("localhost"); // REMOVE THIS LINE

            // ⚠️ CRITICAL: Remove the custom header that set "SameSite=None" without "Secure", 
            // which caused the cookie to be blocked. Spring's default settings are fine here.
            // response.addHeader("Set-Cookie", ...); // REMOVE THIS LINE
            
            response.addCookie(cookie);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Login successful");
            responseBody.put("role", user.getRole().name());
            responseBody.put("username", user.getUsername());

            return ResponseEntity.ok(responseBody);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }
}