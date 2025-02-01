package edu.northeastern.csye6225.webapp.Dao;

import edu.northeastern.csye6225.webapp.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenDao extends JpaRepository<VerificationToken, Long> {

    // Find a token by its value
    Optional<VerificationToken> findByToken(String token);

    // Delete all tokens associated with a specific user ID
    void deleteAllByUser_Id(UUID userId); // Corrected to navigate the user relationship


}