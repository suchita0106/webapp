package edu.northeastern.csye6225.webapp.config;

import com.timgroup.statsd.StatsDClient;
import edu.northeastern.csye6225.webapp.filter.GetRequestAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private StatsDClient statsDClient;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //builder pattern
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new GetRequestAuthFilter(statsDClient), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        //.requestMatchers("/api/v1/users/register", "/healthz").permitAll()
                        .requestMatchers( "/healthz").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/verify**").permitAll()  // Completely skip authentication for this POST
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()  // Completely skip authentication for this POST
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/pic").authenticated() // Require authentication for POST /pic
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/pic").authenticated() // Require authentication for DELETE /pic
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/pic").authenticated() // Require authentication for DELETE /pic
                        //.requestMatchers(HttpMethod.GET, "/api/v1/").permitAll()  // Allow GET request for a specific endpoint
                        .requestMatchers(HttpMethod.PATCH).permitAll()  // Allow PATCH globally
                        .requestMatchers(HttpMethod.HEAD).permitAll()   // Allow HEAD globally
                        .requestMatchers(HttpMethod.DELETE).permitAll() // Allow DELETE globally
                        .requestMatchers(HttpMethod.TRACE).permitAll() // Allow TRACE globally
                        .requestMatchers(HttpMethod.OPTIONS).permitAll() // Allow OPTIONS globally
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS","TRACE","HEAD")
                        .allowedOrigins("*")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization");
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }



}