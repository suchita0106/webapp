package edu.northeastern.csye6225.webapp.config;

import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class StatsDConfig {
    @Value("${metrics.enabled:false}") // Default to false if not set
    private boolean metricsEnabled;

    @Bean
    public StatsDClient appStatsClient() {
        System.out.println("Inside appStatsClient");
        StatsDClient client = metricsEnabled
                ? new NonBlockingStatsDClient("csye6225.webapp", "localhost", 8125)
                : new NoOpStatsDClient();

        System.out.println("StatsDClient initialized as: " + client.getClass().getSimpleName());

        return client;
    }
}