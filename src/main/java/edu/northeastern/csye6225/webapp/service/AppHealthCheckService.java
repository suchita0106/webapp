package edu.northeastern.csye6225.webapp.service;

import edu.northeastern.csye6225.webapp.Dao.AppHealthCheckDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.timgroup.statsd.StatsDClient;

@Service
public class AppHealthCheckService {

    @Autowired
    private AppHealthCheckDao appHealthCheckDAO;

    @Autowired
    private StatsDClient statsDClient;

    private static final Logger logger = LoggerFactory.getLogger(AppHealthCheckService.class);

    public boolean checkDatabaseConnection() {
        statsDClient.incrementCounter("api.healthcheck.dbConnectionCheck");
        long start = System.currentTimeMillis();

        logger.info("Checking DB connection");
        try {
            appHealthCheckDAO.checkDBConnection();
            statsDClient.incrementCounter("api.healthcheck.dbConnectionCheck.success");
            return true;
        } catch (Exception e) {
            logger.error("Database connection failed", e);
            statsDClient.incrementCounter("api.healthcheck.dbConnectionCheck.failure");
            return false;
        } finally {
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.healthcheck.dbConnectionCheck.time.milliseconds", end - start);
        }
    }
}