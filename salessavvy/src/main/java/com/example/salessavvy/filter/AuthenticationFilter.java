package com.example.salessavvy.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set; // Use Set for better performance

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.salessavvy.entities.Role;
import com.example.salessavvy.entities.User;
import com.example.salessavvy.repositories.UserRepository;
import com.example.salessavvy.services.AuthService;

// REMOVE @WebFilter when using FilterRegistrationBean

public class AuthenticationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final AuthService authService;
    private final UserRepository userRepository;

    // Use Set for fast lookup
    private static final Set<String> UNAUTHENTICATED_PATHS = Set.of(
        "/api/users/register",
        "/api/auth/login"
    );
    
    // ✅ Use same origin as your React dev server
    private static final String ALLOWED_ORIGIN = "http://localhost:5173"; 

    public AuthenticationFilter(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Set CORS headers early to allow pre-flight or error messages to be read
        setCORSHeaders((HttpServletResponse) response); 
        try {
            executeFilterLogic((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } catch (Exception e) {
            logger.error("Unexpected error in AuthenticationFilter", e);
            sendErrorResponse((HttpServletResponse) response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error");
        }
    }

    private void executeFilterLogic(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
            throws IOException, ServletException {

        String requestURI = httpRequest.getRequestURI();
        logger.info("Request URI: {}", requestURI);

        // Allow unauthenticated paths
        if (UNAUTHENTICATED_PATHS.contains(requestURI)) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        // Handle preflight (OPTIONS) requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK); 
            return; // Terminate filter chain for preflight
        }

        // --- Authentication Check ---
        String token = getAuthTokenFromCookies(httpRequest);
        logger.debug("Auth token: {}", token);

        if (token == null || !authService.validateToken(token)) {
            sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid or missing token");
            return;
        }

        // Extract username and verify user
        String username = authService.extractUsername(token);
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: User not found");
            return;
        }

        // Get authenticated user and role
        User authenticatedUser = userOptional.get();
        Role role = authenticatedUser.getRole();
        logger.info("Authenticated User: {}, Role: {}", authenticatedUser.getUsername(), role);

        // --- Role-based access control ---
        if (requestURI.startsWith("/admin/") && role != Role.ADMIN) {
            sendErrorResponse(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Admin access required");
            return;
        }

        // ✅ Attach user details to request (makes the controllers work)
        httpRequest.setAttribute("authenticatedUser", authenticatedUser);

        // Continue filter chain
        chain.doFilter(httpRequest, httpResponse);
    }

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json"); 
        // Write JSON error object for API consistency
        response.getWriter().write(String.format("{\"error\": \"%s\", \"status\": %d}", message, statusCode));
    }

    private String getAuthTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(cookie -> "authToken".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}