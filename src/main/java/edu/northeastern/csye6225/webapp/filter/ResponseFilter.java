package edu.northeastern.csye6225.webapp.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import com.timgroup.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ResponseFilter implements Filter {

    private final StatsDClient statsDClient;
    public ResponseFilter(StatsDClient statsDClient) {
        this.statsDClient = statsDClient;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletResponse instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setHeader("cache-control", "no-cache, no-store, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // Increment the response filter metric for tracking
            statsDClient.incrementCounter("response.filter.invoked");

            // Call the next filter in the chain if any
            try {
                filterChain.doFilter(servletRequest, httpResponse);
                // Increment a success metric after a successful filter processing
                statsDClient.incrementCounter("response.filter.success");
            } catch (Exception e) {
                // Increment an error metric if an exception occurs during filtering
                statsDClient.incrementCounter("response.filter.error");
                throw e; // Re-throw the exception to propagate the error
            }
        } else {
            // Increment the response filter metric even when HttpServletResponse is not used
            statsDClient.incrementCounter("response.filter.invoked");
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}