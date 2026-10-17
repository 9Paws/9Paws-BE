package com.ninepaws.backend.services.impl;

import com.ninepaws.backend.constants.RedisKeyConstants;
import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.exceptions.RateLimitException;
import com.ninepaws.backend.services.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final Duration OTP_TTL = Duration.ofSeconds(120);
    private static final Duration OTP_COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(10);
    private static final String COOLDOWN_VALUE = "SENT";
    private static final int OTP_BOUND = 1_000_000;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = initSecureRandom();

    @Override
    public String generateAndStoreOtp(String email, OtpType type) {
        String otp = generateOtp();
        String otpKey = RedisKeyConstants.otpKey(type, email);
        String cooldownKey = RedisKeyConstants.otpCooldownKey(type, email);

        redisTemplate.opsForValue().set(otpKey, otp, OTP_TTL);
        redisTemplate.opsForValue().set(cooldownKey, COOLDOWN_VALUE, OTP_COOLDOWN_TTL);

        log.debug("Generated and stored OTP for email: {}, type: {}", email, type);
        return otp;
    }

    @Override
    public boolean verifyOtp(String email, OtpType type, String otp) {
        if (otp == null) {
            return false;
        }
        String otpKey = RedisKeyConstants.otpKey(type, email);
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp != null && storedOtp.equals(otp)) {
            redisTemplate.delete(otpKey);
            log.debug("Successfully verified and deleted OTP for email: {}, type: {}", email, type);
            return true;
        }
        log.debug("Failed OTP verification for email: {}, type: {}", email, type);
        return false;
    }

    @Override
    public void checkCooldown(String email, OtpType type) {
        String cooldownKey = RedisKeyConstants.otpCooldownKey(type, email);
        Boolean hasCooldown = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new RateLimitException("OTP cooldown active. Please wait 60 seconds before requesting a new OTP.");
        }
    }

    @Override
    public String generateAndStorePasswordResetToken(String email) {
        String token = UUID.randomUUID().toString();
        String key = RedisKeyConstants.passwordResetTokenKey(email);
        redisTemplate.opsForValue().set(key, token, PASSWORD_RESET_TOKEN_TTL);
        log.debug("Generated and stored password reset token for email: {}", email);
        return token;
    }

    @Override
    public boolean verifyPasswordResetToken(String email, String token) {
        if (token == null) {
            return false;
        }
        String key = RedisKeyConstants.passwordResetTokenKey(email);
        String storedToken = redisTemplate.opsForValue().get(key);
        if (storedToken != null && storedToken.equals(token)) {
            redisTemplate.delete(key);
            log.debug("Successfully verified and deleted password reset token for email: {}", email);
            return true;
        }
        log.debug("Failed password reset token verification for email: {}", email);
        return false;
    }

    private String generateOtp() {
        int number = secureRandom.nextInt(OTP_BOUND);
        return String.format("%06d", number);
    }

    private static SecureRandom initSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            log.warn("Strong SecureRandom not available, falling back to default SecureRandom", e);
            return new SecureRandom();
        }
    }
}
