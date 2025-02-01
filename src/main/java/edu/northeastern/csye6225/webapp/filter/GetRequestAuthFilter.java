package edu.northeastern.csye6225.webapp.filter;

import edu.northeastern.csye6225.webapp.model.User;
import edu.northeastern.csye6225.webapp.service.UserService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.stereotype.Component;
import edu.northeastern.csye6225.webapp.service.AppHealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import com.timgroup.statsd.StatsDClient;

@Component
public class GetRequestAuthFilter implements Filter {
    @Autowired
    private AppHealthCheckService appHealthCheckService;

    @Autowired
    private UserService userService;

    private final StatsDClient statsDClient;
    public GetRequestAuthFilter(StatsDClient statsDClient) {
        this.statsDClient = statsDClient;
    }

    // Define the allowed HTTP methods, including DELETE
    private static final List<String> ALLOWED_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE");

    private static final List<String> PUBLIC_URIS = Arrays.asList("/api/v1/users/register", "/healthz", "/api/v1/users/verify");


    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        // Convert the request and response objects to Http versions
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Increment total request metrics
        statsDClient.incrementCounter("api.request.total");
        statsDClient.incrementCounter("api.request." + method.toLowerCase());

        if ("HEAD".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            statsDClient.incrementCounter("api.request.head.failure");
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            // Return 405 Method Not Allowed
            httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            statsDClient.incrementCounter("api.request.options.failure");
            return;
        }

        // Allow DELETE and POST for /api/v1/users/pic with Authorization header
        if ("/api/v1/users/pic".equals(requestURI) &&
                ("DELETE".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))) {
            String authorizationHeader = httpRequest.getHeader("Authorization");

            if (authorizationHeader == null || authorizationHeader.isEmpty()) {
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                String jsonMessage = "{\"message\": \"" + method + " request requires Authorization header.\"}";
                httpResponse.getWriter().write(jsonMessage);
                statsDClient.incrementCounter("api.request.authHeader.missing");
                return; // Stop further processing
            }
        } else if ("DELETE".equalsIgnoreCase(method)) {
            // Reject other DELETE requests
            httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED); // 405
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            String jsonMessage = "{\"message\": \"DELETE method is not allowed for this endpoint.\"}";
            httpResponse.getWriter().write(jsonMessage);
            statsDClient.incrementCounter("api.request.delete.failure");
            return; // Stop further processing
        }

        // Check if the method is unsupported
        if (!ALLOWED_METHODS.contains(method)) {
            httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED); // 405
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            String jsonMessage = "{\"message\": \"HTTP method " + method + " is not supported.\"}";
            httpResponse.getWriter().write(jsonMessage);
            statsDClient.incrementCounter("api.request.unsupportedMethod");
            return; // Stop further processing
        }

        if ("/healthz".equals(requestURI) && ("GET".equalsIgnoreCase(httpRequest.getMethod()))) {
            // Check if the Authorization header is present
            String authorizationHeader = httpRequest.getHeader("Authorization");

            if (authorizationHeader != null) {
                // If Authorization header is present, return 400 Bad Request
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                String jsonMessage = "{\"message\": \"healthz request with Authorization header is not required.\"}";
                httpResponse.getWriter().write(jsonMessage);
                statsDClient.incrementCounter("api.request.healthz.authHeaderPresent");
                return; // Stop further processing
            } else {
                chain.doFilter(request, response);
                statsDClient.incrementCounter("api.request.healthz.success");
                return;
            }
        }

        if ("/healthz".equals(requestURI)) {
            chain.doFilter(request, response);
            statsDClient.incrementCounter("api.request.healthz.success");
            return;
        }

        // Check if the request is a POST request
        if ("POST".equalsIgnoreCase(httpRequest.getMethod())) {
            // Check if the Authorization header is present
            String authorizationHeader = httpRequest.getHeader("Authorization");

            if ("/api/v1/users/pic".equals(requestURI)) {
                if (authorizationHeader == null || authorizationHeader.isEmpty()) {
                    httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    String jsonMessage = "{\"message\": \"" + method + " profile picture request requires Authorization header.\"}";
                    httpResponse.getWriter().write(jsonMessage);
                    statsDClient.incrementCounter("api.request.authHeader.missing");
                    return; // Stop further processing
                }
            } else {
                if (authorizationHeader != null) {
                    // If Authorization header is present, return 400 Bad Request
                    httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    String jsonMessage = "{\"message\": \"POST request with Authorization header is not required.\"}";
                    httpResponse.getWriter().write(jsonMessage);
                    statsDClient.incrementCounter("api.request.postWithAuthHeader.failure");
                    return; // Stop further processing
                }
            }
        }

        if ("GET".equalsIgnoreCase(httpRequest.getMethod())) {
            if ("/api/v1/users/verify".equals(requestURI)) {
                if ( httpRequest.getHeader("Authorization") != null ) {
                    sendErrorResponse(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Authorization header is not allowed for verify");
                    statsDClient.incrementCounter("api.request.user.verifyWithAuthHeader.failure");
                } else {
                    chain.doFilter(request, response);
                    statsDClient.incrementCounter("api.request.user.verify");
                }
                return;
            } else {
                // Check if the Authorization header is present
                String authorizationHeader = httpRequest.getHeader("Authorization");

                if (authorizationHeader == null) {
                    // If Authorization header is present, return 400 Bad Request or 405 Method Not Allowed
                    httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST); // You can use SC_BAD_REQUEST (400) instead

                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    String jsonMessage = "{\"message\": \"GET request except verify requires Authorization header.\"}";
                    httpResponse.getWriter().write(jsonMessage);
                    statsDClient.incrementCounter("api.request.authHeader.missing");
                    return; // Stop further processing
                }
            }
        }

        if ("PUT".equalsIgnoreCase(httpRequest.getMethod())) {
            // Check if the Authorization header is present
            String authorizationHeader = httpRequest.getHeader("Authorization");

            if (authorizationHeader == null) {
                // If Authorization header is present, return 400 Bad Request or 405 Method Not Allowed
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST); // You can use SC_BAD_REQUEST (400) instead

                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                String jsonMessage = "{\"message\": \"PUT request requires Authorization header.\"}";
                httpResponse.getWriter().write(jsonMessage);
                statsDClient.incrementCounter("api.request.authHeader.missing");

                return; // Stop further processing
            }
        }

        // Lazy loading of the AppHealthCheckService
        if (appHealthCheckService == null) {
            WebApplicationContext webApplicationContext = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(httpRequest.getServletContext());
            appHealthCheckService = webApplicationContext.getBean(AppHealthCheckService.class);
        }

        if (userService == null) {
            WebApplicationContext webApplicationContext = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(httpRequest.getServletContext());
            userService = webApplicationContext.getBean(UserService.class);
        }


        // Check if the database connection is available
        if (!appHealthCheckService.checkDatabaseConnection()) {
            httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            String jsonMessage = "{\"message\": \"Service unavailable due to database connection issue.\"}";
            httpResponse.getWriter().write(jsonMessage);
            statsDClient.incrementCounter("api.request.dbConnection.failure");
            return;
        }

        // Check if the request is for a public URI
        if (PUBLIC_URIS.contains(requestURI)) {
            if ("/api/v1/users/register".equals(requestURI) && httpRequest.getHeader("Authorization") != null) {
                sendErrorResponse(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Authorization header is not allowed for register");
                statsDClient.incrementCounter("api.request.registerWithAuthHeader.failure");
                return;
            }
            // Allow public URIs to proceed
            chain.doFilter(request, response);
            statsDClient.incrementCounter("api.request.public.success");
            return;
        }

        // Verify user for non-public URIs
        String authorizationHeader = httpRequest.getHeader("Authorization");
        String email = extractEmailFromBasicAuth(authorizationHeader);
        if (email == null || email.isEmpty()) {
            sendErrorResponse(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Invalid Authorization header");
            statsDClient.incrementCounter("api.request.authHeader.invalid");
            return;
        }

        // Fetch the user and verify their status
        User user = userService.findByEmail(email);

        if (user == null) {
            System.out.println("User not found for email: " + email);
            sendErrorResponse(httpResponse, HttpServletResponse.SC_FORBIDDEN, "User does not exist.");
            statsDClient.incrementCounter("api.request.user.notFound");
            return;
        }

        if (!user.isVerified()) {
            System.out.println("User found but not verified: " + user.getEmail());
            sendErrorResponse(httpResponse, HttpServletResponse.SC_FORBIDDEN, "User is not verified. Access is restricted.");
            statsDClient.incrementCounter("api.request.user.notVerified");
            return;
        }


        // Continue with the rest of the filter chain if conditions are not met
        chain.doFilter(request, response);
        statsDClient.incrementCounter("api.request.success");
    }

    private void sendErrorResponse(HttpServletResponse httpResponse, int statusCode, String message) throws IOException {
        httpResponse.setStatus(statusCode);
        httpResponse.setContentType("application/json");
        httpResponse.setCharacterEncoding("UTF-8");
        String jsonMessage = "{\"message\": \"" + message + "\"}";
        httpResponse.getWriter().write(jsonMessage);
    }

    private String extractEmailFromBasicAuth(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            try {
                // Decode the base64-encoded credentials
                String base64Credentials = authorizationHeader.substring("Basic ".length());
                String credentials = new String(Base64.getDecoder().decode(base64Credentials));

                // Split into username and password
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
                    return parts[0]; // Username (email in this case)
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error decoding Authorization header: " + e.getMessage());
            }
        }
        return null; // Return null if decoding fails or header is invalid
    }

}