package com.ninepaws.backend.configs;

import com.ninepaws.backend.constants.RedisKeyConstants;
import com.ninepaws.backend.enums.OtpType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisKeyConstantsTest {

    @Test
    @DisplayName("Should generate consistent and correct Redis keys according to defined patterns")
    void shouldGenerateConsistentRedisKeys() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(RedisKeyConstants.otpKey(OtpType.REGISTER, "test@example.com"))
                .isEqualTo("9paws:otp:REGISTER:test@example.com");

        assertThat(RedisKeyConstants.otpCooldownKey(OtpType.REGISTER, "test@example.com"))
                .isEqualTo("9paws:otp:cooldown:REGISTER:test@example.com");

        assertThat(RedisKeyConstants.otpKey(OtpType.FORGOT_PASSWORD, "user@ninepaws.com"))
                .isEqualTo("9paws:otp:FORGOT_PASSWORD:user@ninepaws.com");

        assertThat(RedisKeyConstants.otpCooldownKey(OtpType.FORGOT_PASSWORD, "user@ninepaws.com"))
                .isEqualTo("9paws:otp:cooldown:FORGOT_PASSWORD:user@ninepaws.com");

        assertThat(RedisKeyConstants.refreshTokenKey(userId, "token-jti-123"))
                .isEqualTo("9paws:refresh_token:11111111-1111-1111-1111-111111111111:token-jti-123");

        assertThat(RedisKeyConstants.userSessionsKey(userId))
                .isEqualTo("9paws:user_sessions:11111111-1111-1111-1111-111111111111");

        assertThat(RedisKeyConstants.tokenBlacklistKey("token-jti-123"))
                .isEqualTo("9paws:token:blacklist:token-jti-123");

        assertThat(RedisKeyConstants.passwordResetTokenKey("test@example.com"))
                .isEqualTo("9paws:password_reset_token:test@example.com");
    }

    @Test
    @DisplayName("Private constructor of RedisKeyConstants should be invokable via reflection for coverage")
    void shouldInvokePrivateConstructor() throws Exception {
        Constructor<RedisKeyConstants> constructor = RedisKeyConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RedisKeyConstants instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("RedisConfig should configure StringRedisTemplate with StringRedisSerializer for keys and values")
    void shouldConfigureStringRedisTemplate() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        StringRedisTemplate template = config.stringRedisTemplate(connectionFactory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(StringRedisSerializer.class);
    }
}
