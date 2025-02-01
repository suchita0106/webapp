package edu.northeastern.csye6225.webapp.Dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.timgroup.statsd.StatsDClient;

import javax.sql.DataSource;
import java.sql.Connection;

@Repository
public class AppHealthCheckDao {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private StatsDClient statsDClient;

    private static final Logger logger = LoggerFactory.getLogger(AppHealthCheckDao.class);

    public void checkDBConnection() throws Exception {
        long startTime = System.currentTimeMillis(); // Start the timer

        try (Connection connection = dataSource.getConnection()) {
            logger.info("DB connection successful.");
            // Increment a counter for successful DB connection checks
            statsDClient.incrementCounter("db.connection.success");
        } catch (Exception e) {
            // Increment a counter for failed DB connection checks
            statsDClient.incrementCounter("db.connection.failure");
            throw e;
        } finally {
            long endTime = System.currentTimeMillis(); // End the timer
            long duration = endTime - startTime;
            // Record the time taken for the DB connection check
            statsDClient.recordExecutionTime("db.connection.time.milliseconds", duration);
            logger.info("DB connection check took {} ms.", duration);
        }
    }
}