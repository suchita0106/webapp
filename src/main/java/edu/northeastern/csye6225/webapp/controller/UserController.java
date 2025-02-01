package edu.northeastern.csye6225.webapp.controller;

import com.timgroup.statsd.StatsDClient;
import edu.northeastern.csye6225.webapp.exception.ResourceNotFoundException;
import edu.northeastern.csye6225.webapp.model.User;
import edu.northeastern.csye6225.webapp.service.UserService;
import edu.northeastern.csye6225.webapp.dto.UserDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;
import edu.northeastern.csye6225.webapp.dto.ImageDTO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StatsDClient statsDClient;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity<Map<String, String>> registerUser(@RequestBody @Valid User user, HttpServletRequest request) {
        statsDClient.incrementCounter("api.user.register");
        Map<String, String> response = new HashMap<>();
        if (request.getQueryString() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);  // Return 400 Bad Request
        }

        if (user.getAccountCreated() != null || user.getAccountUpdated() != null) {
            response.put("error", "Fields 'accountCreated' and 'accountUpdated' cannot be provided in the request.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        try {
            long start = System.currentTimeMillis();
            userService.createUser(user);
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.user.register.time.milliseconds", end - start);
            response.put("message", "User created successfully");
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);
        } catch (IllegalArgumentException e) {
            statsDClient.incrementCounter("api.user.register.failure");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyUser(HttpServletRequest request) {
        statsDClient.incrementCounter("api.user.verify");
        Map<String, String> response = new HashMap<>();

        try {
            // Validate that only 'user' and 'token' parameters are present
            Map<String, String[]> queryParams = request.getParameterMap();

            if (queryParams.size() != 2 || !queryParams.containsKey("user") || !queryParams.containsKey("token")) {
                response.put("error", "Invalid query parameters. Only 'user' and 'token' are allowed.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Extract the 'user' and 'token' parameter values
            String inputuser = queryParams.get("user")[0];
            String inputToken = queryParams.get("token")[0];

            if (inputuser == null || inputuser.isEmpty()) {
                response.put("error", "User parameter is missing or empty.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (inputToken == null || inputToken.isEmpty()) {
                response.put("error", "Token parameter is missing or empty.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Call the service to verify the user
            boolean verified = userService.verifyUser(inputToken,inputuser);
            if (verified) {
                response.put("message", "User verified successfully.");
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

            // Token is invalid or expired
            response.put("error", "Invalid or expired token.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (ResourceNotFoundException e){
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }catch (Exception e) {
            // Handle unexpected errors
            response.put("error", "Invalid query string");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }


    @GetMapping("/self")
    public ResponseEntity<UserDTO> getUser(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        statsDClient.incrementCounter("api.user.getSelf");
        if (request.getContentLength() > 0 || request.getQueryString() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);  // Return 400 Bad Request
        }

        User user = userService.findByEmail(userDetails.getUsername());
        if (user == null) {
            statsDClient.incrementCounter("api.user.getSelf.failure");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        UserDTO userDTO = userService.convertToDTO(user);
        return ResponseEntity.ok()
                .body(userDTO);
    }

    @PutMapping(value = "/update", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UserDTO> updateUser(@RequestBody User user, @AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        statsDClient.incrementCounter("api.user.update");
        if (request.getQueryString() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);  // Return 400 Bad Request
        }

        if (user.getAccountCreated() != null || user.getAccountUpdated() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }

        if (!userDetails.getUsername().equals(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);  // User can only update their own information
        }

        long start = System.currentTimeMillis();
        User updatedUser = userService.updateUser(user);
        long end = System.currentTimeMillis();
        statsDClient.recordExecutionTime("api.user.update.time.milliseconds", end - start);

        UserDTO updatedUserDTO = userService.convertToDTO(updatedUser);
        return ResponseEntity.ok()
                .body(updatedUserDTO);
    }

    @PostMapping(value = "/pic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadProfilePicture(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        statsDClient.incrementCounter("api.user.uploadProfilePicture");

        Map<String, String> response = new HashMap<>();

        String contentType = request.getContentType();
        if (contentType == null || !contentType.startsWith("multipart/")) {
            response.put("error", "Request content type must be multipart/form-data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        // Check if file is present and not empty
        if (file == null || file.isEmpty()) {
            response.put("error", "File is missing in the request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        // Validate MIME type and file extension
        String fileType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (!isValidImageType(fileType, originalFilename)) {
            response.put("error", "Unsupported file type. Only PNG, JPG, and JPEG formats are allowed.");
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE) // 415 Unsupported Media Type
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        try {
            // Check if the profile picture already exists for the user
            User existingUser = userService.findByEmail(userDetails.getUsername());
            if (existingUser.getProfileImage() != null) {
                response.put("error", "Profile picture already present");
                return ResponseEntity.status(HttpStatus.CONFLICT) // 409 Conflict
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response);
            }

            long start = System.currentTimeMillis();

            ImageDTO imageDTO = userService.updateUserProfilePicture(userDetails.getUsername(), file);

            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.user.uploadProfilePicture.time.milliseconds", end - start);

            // Return the image metadata as JSON
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(imageDTO);
        } catch (IOException e) {
            statsDClient.incrementCounter("api.user.uploadProfilePicture.failure");
            logger.error("Failed to upload profile picture for user {} due to IOException: {}", userDetails.getUsername(), e.getMessage());
            logger.error("Exception Stack Trace: ", e);

            response.put("error", "Failed to upload profile picture");
            response.put("exceptionMessage", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
    }

    // Helper method to check if the file type is valid based on MIME type and file extension
    private boolean isValidImageType(String fileType, String fileName) {
        if (fileType != null && (fileType.equals("image/png") || fileType.equals("image/jpeg"))) {
            return true;
        }
        // Check file extension as a fallback
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");
    }

    @DeleteMapping("/pic")
    public ResponseEntity<Map<String, String>> deleteProfilePicture(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        statsDClient.incrementCounter("api.user.deleteProfilePicture");

        Map<String, String> response = new HashMap<>();
        // Check if request contains a payload
        if (request.getContentLength() > 0) {
            response.put("error", "DELETE request should not contain a payload.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        try {
            User existingUser = userService.findByEmail(userDetails.getUsername());
            if (existingUser.getProfileImage() == null) {
                response.put("error", "Profile picture not present");
                return ResponseEntity.status(HttpStatus.NOT_FOUND) // 409 Conflict
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response);
            }

            userService.deleteUserProfilePicture(userDetails.getUsername());

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.deleteProfilePicture.failure");
            logger.error("Failed to delete profile picture for user {} due to Exception: {}", userDetails.getUsername(), e.getMessage());

            response.put("error", "Failed to delete profile picture");
            response.put("exceptionMessage", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
    }

    @GetMapping(value = "/pic", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProfilePictureMetadata(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        statsDClient.incrementCounter("api.user.getProfilePictureMetadata");
        Map<String, String> response = new HashMap<>();

        // Check if request contains a payload (should not have one)
        if (request.getContentLength() > 0) {
            response.put("error", "GET request should not contain a payload.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        // Retrieve user and image metadata
        User user = userService.findByEmail(userDetails.getUsername());
        if (user == null || user.getProfileImage() == null) {
            response.put("error", "Profile picture not found for the user.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }

        // Convert image details to ImageDTO for structured response
        ImageDTO imageDTO = new ImageDTO(
                user.getProfileImage().getFileName(),
                user.getProfileImage().getId(),
                user.getProfileImage().getUrl(),
                user.getProfileImage().getUploadDate(),
                user.getId()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(imageDTO);
    }

    @RequestMapping(value = {"/", "/**", "/self"}, method = {RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD, RequestMethod.TRACE})
    public ResponseEntity<Void> handleUnsupportedMethods(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
}