package com.omtb.backend.controllers;

import com.omtb.backend.models.User;
import com.omtb.backend.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        Map<String, Object> response = new HashMap<>();

        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            response.put("success", false);
            response.put("message", "Email is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepository.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            response.put("success", false);
            response.put("message", "Error: Email is already in use!");
            return ResponseEntity.badRequest().body(response);
        }

        if (user.getUsername() != null && userRepository.findByUsernameIgnoreCase(user.getUsername()).isPresent()) {
            response.put("success", false);
            response.put("message", "Error: Username is already taken!");
            return ResponseEntity.badRequest().body(response);
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            response.put("success", false);
            response.put("message", "Password is required");
            return ResponseEntity.badRequest().body(response);
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("customer");
        userRepository.save(user);

        response.put("success", true);
        response.put("message", "User registered successfully!");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> loginRequest) {
        Map<String, Object> response = new HashMap<>();
        String identifier = loginRequest.get("identifier");
        String password = loginRequest.get("password");

        if (identifier == null || identifier.isBlank() || password == null) {
            response.put("success", false);
            response.put("message", "Email/username and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        String normalizedIdentifier = identifier.trim();
        Optional<User> authenticatedUser = userRepository.findByEmailIgnoreCase(normalizedIdentifier)
                .filter(user -> passwordMatches(password, user.getPassword()))
                .or(() -> userRepository.findByUsernameIgnoreCase(normalizedIdentifier)
                        .filter(user -> passwordMatches(password, user.getPassword())));

        return authenticatedUser
                .map(user -> {
                    if (!isBcryptPassword(user.getPassword())) {
                        user.setPassword(passwordEncoder.encode(password));
                        userRepository.save(user);
                    }

                    response.put("success", true);
                    response.put("message", "Login successful");
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("id", user.getId());
                    userData.put("username", user.getUsername());
                    userData.put("name", user.getName());
                    userData.put("email", user.getEmail());
                    userData.put("role", normalizeRole(user.getRole()));
                    response.put("user", userData);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    response.put("success", false);
                    response.put("message", "Invalid email or password");
                    return ResponseEntity.badRequest().body(response);
                });
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("{bcrypt}")) {
            return passwordEncoder.matches(rawPassword, storedPassword.substring("{bcrypt}".length()));
        }
        return isBcryptPassword(storedPassword)
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : rawPassword.equals(storedPassword);
    }

    private boolean isBcryptPassword(String password) {
        if (password == null) {
            return false;
        }
        String hash = password.startsWith("{bcrypt}")
                ? password.substring("{bcrypt}".length())
                : password;
        return hash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.replaceFirst("(?i)^ROLE_", "").toLowerCase(Locale.ROOT);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateUser(@RequestBody User updatedUser) {
        Map<String, Object> response = new HashMap<>();

        return userRepository.findById(updatedUser.getId())
                .map(existingUser -> {
                    if (updatedUser.getUsername() != null && !updatedUser.getUsername().isEmpty()) {
                        existingUser.setUsername(updatedUser.getUsername());
                    }
                    if (updatedUser.getEmail() != null && !updatedUser.getEmail().isEmpty()) {
                        existingUser.setEmail(updatedUser.getEmail());
                    }
                    if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
                        existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
                    }
                    
                    userRepository.save(existingUser);
                    
                    response.put("success", true);
                    response.put("message", "Profile updated successfully");
                    response.put("user", existingUser);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    response.put("success", false);
                    response.put("message", "User not found");
                    return ResponseEntity.badRequest().body(response);
                });
    }
}
