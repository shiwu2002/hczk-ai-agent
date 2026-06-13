package com.hczk.hczkaiagentserver.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.UserRole;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUsers();
    }

    private void initUsers() {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin")) == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@hczk.com");
            admin.setRole(UserRole.ADMIN);
            admin.setBalance(new BigDecimal("10000.00"));
            userMapper.insert(admin);
        }

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "user")) == 0) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setEmail("user@hczk.com");
            user.setRole(UserRole.USER);
            user.setBalance(new BigDecimal("1250.00"));
            userMapper.insert(user);
        }
    }
}
