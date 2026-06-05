package com.github.mail;

import com.github.mail.config.JwtKeyProvider;
import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.ITokenInvalidationService;
import com.github.mail.service.User.JwtTokenService;
import com.github.mail.service.User.impl.AccountAuthServiceImpl;
import com.github.mail.utils.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AccountAuthServiceImplTest {

    @Test
    void registerFirstUserBecomesAdmin() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectCount(any())).thenReturn(0L, 0L);
        when(userMapper.insert(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        });

        AccountAuthProperties props = new AccountAuthProperties();
        props.setJwtSecret("01234567890123456789012345678901");
        PasswordHasher hasher = new PasswordHasher();
        JwtTokenService jwt = new JwtTokenService(new JwtKeyProvider(props), props);
        ITokenInvalidationService tokenInvalidationService = mock(ITokenInvalidationService.class);

        AccountAuthServiceImpl service = new AccountAuthServiceImpl(userMapper, hasher, jwt, props, tokenInvalidationService);
        Users created = service.register("admin", "Passw0rd123");
        assertEquals("ADMIN", created.getRole());
        assertNotNull(created.getPasswordHash());
        assertNull(created.getPasswd());
    }

    @Test
    void loginWithPasswordHashWorks() {
        UserMapper userMapper = mock(UserMapper.class);

        AccountAuthProperties props = new AccountAuthProperties();
        props.setJwtSecret("01234567890123456789012345678901");
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("Passw0rd123");

        Users dbUser = new Users();
        dbUser.setId(1L);
        dbUser.setUser("u1");
        dbUser.setPasswordHash(hash);
        dbUser.setStatus("ACTIVE");
        dbUser.setRole("USER");
        dbUser.setIsDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(dbUser);

        JwtTokenService jwt = new JwtTokenService(new JwtKeyProvider(props), props);
        ITokenInvalidationService tokenInvalidationService = mock(ITokenInvalidationService.class);
        AccountAuthServiceImpl service = new AccountAuthServiceImpl(userMapper, hasher, jwt, props, tokenInvalidationService);

        String token = service.login("u1", "Passw0rd123");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void disabledUserCannotLogin() {
        UserMapper userMapper = mock(UserMapper.class);

        AccountAuthProperties props = new AccountAuthProperties();
        props.setJwtSecret("01234567890123456789012345678901");
        PasswordHasher hasher = new PasswordHasher();

        Users dbUser = new Users();
        dbUser.setId(1L);
        dbUser.setUser("u1");
        dbUser.setPasswordHash(hasher.hash("Passw0rd123"));
        dbUser.setStatus("DISABLED");
        dbUser.setRole("USER");
        dbUser.setIsDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(dbUser);

        JwtTokenService jwt = new JwtTokenService(new JwtKeyProvider(props), props);
        ITokenInvalidationService tokenInvalidationService = mock(ITokenInvalidationService.class);
        AccountAuthServiceImpl service = new AccountAuthServiceImpl(userMapper, hasher, jwt, props, tokenInvalidationService);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.login("u1", "Passw0rd123"));
        assertEquals("账户不可用", ex.getMessage());
    }

    @Test
    void logoutWhenEnabledInvalidatesToken() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectCount(any())).thenReturn(0L, 0L);

        AccountAuthProperties props = new AccountAuthProperties();
        props.setJwtSecret("01234567890123456789012345678901");
        props.setServerLogoutEnabled(true);
        PasswordHasher hasher = new PasswordHasher();
        JwtTokenService jwt = new JwtTokenService(new JwtKeyProvider(props), props);
        ITokenInvalidationService tokenInvalidationService = mock(ITokenInvalidationService.class);

        AccountAuthServiceImpl service = new AccountAuthServiceImpl(userMapper, hasher, jwt, props, tokenInvalidationService);
        service.logout("jti-1", LocalDateTime.now().plusDays(1));
        verify(tokenInvalidationService, times(1)).invalidate(eq("jti-1"), any());
    }
}
