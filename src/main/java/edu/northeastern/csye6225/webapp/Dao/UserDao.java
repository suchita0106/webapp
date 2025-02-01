package edu.northeastern.csye6225.webapp.Dao;


import edu.northeastern.csye6225.webapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserDao extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    User findByEmail(String username);
}