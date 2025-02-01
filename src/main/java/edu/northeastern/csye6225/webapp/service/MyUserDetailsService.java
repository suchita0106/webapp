package edu.northeastern.csye6225.webapp.service;

import edu.northeastern.csye6225.webapp.Dao.UserDao;
import edu.northeastern.csye6225.webapp.model.User;
import edu.northeastern.csye6225.webapp.model.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserDao userRepo;

    @Autowired
    private StatsDClient statsDClient;

    private static final Logger logger = LoggerFactory.getLogger(MyUserDetailsService.class);

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        statsDClient.incrementCounter("api.user.loadUserByUsername");
        long start = System.currentTimeMillis();

        try {
            User user = userRepo.findByEmail(username);
            if (user == null) {
                logger.error("User not found with email: {}", username);
                statsDClient.incrementCounter("api.user.loadUserByUsername.error");
                throw new UsernameNotFoundException("User not found");
            }

            logger.info("User found with email: {}", user.getEmail());
            return new UserPrincipal(user);
        } finally {
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.user.loadUserByUsername.time.milliseconds", end - start);
        }
    }
}