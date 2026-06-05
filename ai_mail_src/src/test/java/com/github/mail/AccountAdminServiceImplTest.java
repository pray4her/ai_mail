package com.github.mail;

import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.impl.AccountAdminServiceImpl;
import com.github.mail.utils.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AccountAdminServiceImplTest {

    @Test
    void createUserSetsHashedPassword() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(2L);
            return 1;
        });

        AccountAuthProperties props = new AccountAuthProperties();
        props.setMinPasswordLength(8);
        AccountAdminServiceImpl service = new AccountAdminServiceImpl(userMapper, new PasswordHasher(), props);

        Users created = service.createUser("u2", "Passw0rd123", "USER");
        assertEquals("u2", created.getUser());
        assertNotNull(created.getPasswordHash());
        assertNull(created.getPasswd());
        assertEquals("USER", created.getRole());
    }
}
