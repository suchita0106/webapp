package edu.northeastern.csye6225.webapp.service;

import com.timgroup.statsd.StatsDClient;
import edu.northeastern.csye6225.webapp.Dao.UserDao;
import edu.northeastern.csye6225.webapp.Dao.VerificationTokenDao;
import edu.northeastern.csye6225.webapp.exception.ResourceNotFoundException;
import edu.northeastern.csye6225.webapp.model.User;
import edu.northeastern.csye6225.webapp.model.VerificationToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserServiceUnitTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDao userDao;

    @Mock
    private StatsDClient statsDClient;

    @Mock
    private VerificationTokenDao verificationTokenDao;

    @Mock
    private SnsClient snsClient;

    private User user;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new User();
        user.setEmail("test@domain.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPassword("password");
        user.setAccountCreated(new Date());
        user.setAccountUpdated(new Date());
        //System.setProperty("metrics.enabled", "false");
    }

    @Test
    public void testCreateUser_Success() {
        // Mock TypedQuery behavior
        TypedQuery<Long> mockedQuery = mock(TypedQuery.class);
        when(mockedQuery.setParameter(anyString(), any())).thenReturn(mockedQuery);
        when(mockedQuery.getSingleResult()).thenReturn(0L); // Simulate no user with this email

        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(mockedQuery);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        // Mock verification token save behavior
        VerificationToken mockedToken = new VerificationToken();
        mockedToken.setToken("mockedToken");
        when(verificationTokenDao.save(any(VerificationToken.class))).thenReturn(mockedToken);

        // Call the method under test
        userService.createUser(user);

        // Verify behaviors
        verify(entityManager, times(1)).persist(user);
        verify(statsDClient, atLeastOnce()).incrementCounter(anyString());
        verify(verificationTokenDao, times(1)).save(any(VerificationToken.class)); // Ensure token is saved
    }

    @Test
    public void testCreateUser_EmailAlreadyExists() {
        TypedQuery<Long> mockedQuery = mock(TypedQuery.class);
        when(mockedQuery.setParameter(anyString(), any())).thenReturn(mockedQuery);
        when(mockedQuery.getSingleResult()).thenReturn(1L);  // User already exists

        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(mockedQuery);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(user);
        });

        assertEquals("Email already exists", exception.getMessage());
        verify(statsDClient, atLeastOnce()).incrementCounter(anyString());
    }

    @Test
    public void testFindByEmail_Success() {
        TypedQuery<User> mockedQuery = mock(TypedQuery.class);
        when(mockedQuery.setParameter(anyString(), any())).thenReturn(mockedQuery);
        when(mockedQuery.getSingleResult()).thenReturn(user);

        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(mockedQuery);

        User foundUser = userService.findByEmail("test@domain.com");

        assertNotNull(foundUser);
        assertEquals("test@domain.com", foundUser.getEmail());
        verify(statsDClient, atLeastOnce()).incrementCounter(anyString());
    }

    @Test
    public void testUpdateUser_Success() {
        when(userDao.findByEmail(anyString())).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        User mergedUser = new User();
        mergedUser.setFirstName("Jane");
        mergedUser.setLastName("Smith");
        mergedUser.setEmail("test@domain.com");

        when(entityManager.merge(any(User.class))).thenReturn(mergedUser);

        user.setFirstName("Jane");
        user.setLastName("Smith");

        User updatedUser = userService.updateUser(user);

        verify(entityManager, times(1)).merge(user);
        verify(statsDClient, atLeastOnce()).incrementCounter(anyString());
        assertNotNull(updatedUser);
        assertEquals("Jane", updatedUser.getFirstName());
        assertEquals("Smith", updatedUser.getLastName());
    }


    @Test
    public void testUpdateUser_UserNotFound() {
        when(userDao.findByEmail(anyString())).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> {
            userService.updateUser(user);
        });
        verify(statsDClient, atLeastOnce()).incrementCounter(anyString());
    }
}