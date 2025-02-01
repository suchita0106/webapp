package edu.northeastern.csye6225.webapp.Dao;

import edu.northeastern.csye6225.webapp.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImageDao extends JpaRepository<Image, Long> {
    Image findByUserId(UUID userId);
}