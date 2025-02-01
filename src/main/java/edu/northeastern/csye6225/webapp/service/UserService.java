package edu.northeastern.csye6225.webapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.csye6225.webapp.Dao.ImageDao;
import edu.northeastern.csye6225.webapp.Dao.UserDao;
import edu.northeastern.csye6225.webapp.Dao.VerificationTokenDao;
import edu.northeastern.csye6225.webapp.dto.ImageDTO;
import edu.northeastern.csye6225.webapp.dto.UserDTO;
import edu.northeastern.csye6225.webapp.exception.ResourceNotFoundException;
import edu.northeastern.csye6225.webapp.model.Image;
import edu.northeastern.csye6225.webapp.model.User;
import edu.northeastern.csye6225.webapp.model.VerificationToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import com.timgroup.statsd.StatsDClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ImageDao imageDao;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private StatsDClient statsDClient;
    @Autowired
    private VerificationTokenDao verificationTokenDao;
    @Autowired
    private SnsClient snsClient;
    @Value("${sns.topic.arn}")
    private String snsTopicArn;

    @Transactional
    public User createUser(User user) {
        statsDClient.incrementCounter("api.user.create");
        long start = System.currentTimeMillis();

        if (existsByEmail(user.getEmail())) {
            statsDClient.incrementCounter("api.user.create.error");
            throw new IllegalArgumentException("Email already exists");
        }
        logger.info("User does not exist");

        // Password BCrypt
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setAccountCreated(new Date());
        user.setAccountUpdated(new Date());
        user.setVerified(false);

        entityManager.persist(user);

        // Publish to SNS and generate verification token
        String token = generateVerificationToken(user);
        publishToSns(user, token);

        long end = System.currentTimeMillis();
        statsDClient.recordExecutionTime("api.user.create.time.milliseconds", end - start);

        return user;
    }

    private void deleteUserAndTokens(User user) {
        verificationTokenDao.deleteAllByUser_Id(user.getId());
        userDao.delete(user);
    }

    private void publishToSns(User user, String token) {
        ObjectMapper objectMapper = new ObjectMapper();

        // Create a map to hold the message data
        Map<String, String> messagePayload = new HashMap<>();
        messagePayload.put("email", user.getEmail());
        messagePayload.put("token", token);

        // Serialize the map to a JSON string
        String message = null;
        try {
            message = objectMapper.writeValueAsString(messagePayload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON processing error");
        }

        // Build the PublishRequest with the JSON message
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(snsTopicArn)
                .message(message)
                .build();

        snsClient.publish(publishRequest);
        logger.info("Published message to SNS for user: {}", user.getEmail());
    }

    private String generateVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(120); // 2 minutes expiry

        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(token);
        verificationToken.setExpiry(expiry);

        verificationTokenDao.save(verificationToken);
        logger.info("Generated verification token for user: {}", user.getEmail());
        return token;
    }

    public boolean existsByEmail(String email) {
        logger.info("Check if user already exists in the system.");
        Long count = entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }

    public User findByEmail(String email) {
        statsDClient.incrementCounter("api.user.findByEmail");
        long start = System.currentTimeMillis();

        try {
            return entityManager.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.findByEmail.error");
            return null;
        } finally {
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.user.findByEmail.time.milliseconds", end - start);
        }
    }

    @Transactional
    public User updateUser(User user) {
        statsDClient.incrementCounter("api.user.update");
        long start = System.currentTimeMillis();

        User existingUser = userDao.findByEmail(user.getEmail());
        if (existingUser == null) {
            statsDClient.incrementCounter("api.user.update.error");
            throw new ResourceNotFoundException("User not found with email: " + user.getEmail());
        }

        if (!existingUser.getEmail().equals(user.getEmail())) {
            User userWithSameEmail = userDao.findByEmail(user.getEmail());
            if (userWithSameEmail != null) {
                statsDClient.incrementCounter("api.user.update.error");
                throw new IllegalArgumentException("Email already exists in the system");
            }
        }

        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        existingUser.setAccountUpdated(new Date());
        User updatedUser = entityManager.merge(existingUser);

        long end = System.currentTimeMillis();
        statsDClient.recordExecutionTime("api.user.update.time.milliseconds", end - start);

        return updatedUser;
    }

    @Transactional
    public void deleteUserByEmail(String email) {
        statsDClient.incrementCounter("api.user.delete");
        long start = System.currentTimeMillis();

        User user = findByEmail(email);
        if (user != null) {
            entityManager.remove(user);
        }

        long end = System.currentTimeMillis();
        statsDClient.recordExecutionTime("api.user.delete.time.milliseconds", end - start);
    }

    @Transactional
    public ImageDTO updateUserProfilePicture(String email, MultipartFile file) throws IOException {
        statsDClient.incrementCounter("api.user.updateProfilePicture");
        long start = System.currentTimeMillis();

        User user = userDao.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found with email: " + email);
        }

        Map<String, Object> uploadResult = s3Service.uploadFile(file);

        // Create new Image entity
        Image profileImage = new Image();
        profileImage.setFileName(file.getOriginalFilename());
        profileImage.setUrl((String) uploadResult.get("fileUrl"));
        profileImage.setUploadDate(new Date());
        profileImage.setUser(user);

        // Associate the image with the user
        user.setProfileImage(profileImage);
        user.setAccountUpdated(new Date());

        // Save the Image and update the User
        imageDao.save(profileImage);
        userDao.save(user);

        statsDClient.recordExecutionTime("api.user.updateProfilePicture.time.milliseconds", System.currentTimeMillis() - start);
        ImageDTO imageDTO = new ImageDTO(
                user.getProfileImage().getFileName(),
                user.getProfileImage().getId(),
                user.getProfileImage().getUrl(),
                user.getProfileImage().getUploadDate(),
                user.getId()
        );
        return imageDTO;
    }

    @Transactional
    public void deleteUserProfilePicture(String email) {
        statsDClient.incrementCounter("api.user.deleteProfilePicture");
        long start = System.currentTimeMillis();

        // Fetch user by email
        User user = userDao.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found with email: " + email);
        }

        // Fetch profile image if it exists
        Image profileImage = user.getProfileImage();
        if (profileImage != null) {
            // Delete the file from S3
            s3Service.deleteFile(profileImage.getUrl());

            // Delete the Image record and clear the user's reference
            imageDao.delete(profileImage);
            user.setProfileImage(null);
            user.setAccountUpdated(new Date());

            // Save updated user record
            userDao.save(user);
        } else {
            throw new IllegalArgumentException("No profile picture found to delete.");
        }

        statsDClient.recordExecutionTime("api.user.deleteProfilePicture.time.milliseconds", System.currentTimeMillis() - start);
    }

    @Transactional
    public boolean verifyUser(String token, String useremail) {
        VerificationToken verificationToken = verificationTokenDao.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token"));

        User user = userDao.findById(verificationToken.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if(user.isVerified()){
            throw new ResourceNotFoundException("User already verified");
        }

        if (verificationToken.getExpiry().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Token has expired. Please re-register.");
        }

        if(!useremail.matches(user.getEmail())){
            throw new ResourceNotFoundException("User not found!");
        }


        if (!user.isVerified()) {
                user.setVerified(true);
                user.setAccountUpdated(new Date());
                userDao.save(user);
               // verificationTokenDao.delete(verificationToken);
        }

        return true;
    }

    public UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAccountCreated(),
                user.getAccountUpdated(),
                user.isVerified()
        );
    }
}