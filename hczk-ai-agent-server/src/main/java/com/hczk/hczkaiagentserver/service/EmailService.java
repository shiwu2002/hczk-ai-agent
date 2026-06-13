package com.hczk.hczkaiagentserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.from:${spring.mail.username}}")
    private String fromAddress;

    private static final String CODE_PREFIX = "email:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;

    public void sendVerificationCode(String email) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        String key = CODE_PREFIX + email;

        // 1分钟内不允许重复发送
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > (CODE_EXPIRE_MINUTES * 60 - 60)) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }

        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("桓宸智科AI平台 - 邮箱验证码");
        message.setText("您的验证码为：" + code + "，有效期" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露。");
        mailSender.send(message);
    }

    public boolean verifyCode(String email, String code) {
        String key = CODE_PREFIX + email;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return false;
        }
        if (cached.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
