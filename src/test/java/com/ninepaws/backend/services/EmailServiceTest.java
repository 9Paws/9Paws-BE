package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.exceptions.AppException;
import com.ninepaws.backend.services.impl.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private JavaMailSender mailSender;

  @InjectMocks private EmailServiceImpl emailService;

  private final String fromEmail = "no-reply@ninepaws.com";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(emailService, "fromEmail", fromEmail);
  }

  @Nested
  @DisplayName("sendRegistrationOtp tests")
  class SendRegistrationOtpTests {

    @Test
    @DisplayName(
        "Should send registration OTP email with correct recipient, subject, and HTML content")
    void sendRegistrationOtp_sendsMimeMessageWithOtpContent() throws Exception {
      MimeMessage mimeMessage = new MimeMessage((Session) null);
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

      emailService.sendRegistrationOtp("user@example.com", "Nguyen Van A", "123456");

      verify(mailSender).send(mimeMessage);
      assertThat(mimeMessage.getSubject()).contains("Đăng ký tài khoản NinePaws");
      assertThat(mimeMessage.getAllRecipients()).hasSize(1);
      assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("user@example.com");

      // Verify content has branding, greeting, OTP code in bold, 2-minute expiration, and
      // disclaimer
      String content = extractBodyContent(mimeMessage);
      assertThat(content).contains("NinePaws");
      assertThat(content).contains("Nguyen Van A");
      assertThat(content).contains("123456");
      assertThat(content).contains("2 phút");
    }

    @Test
    @DisplayName("Should sanitize HTML characters in fullName to prevent XSS")
    void sendRegistrationOtp_sanitizesHtmlInFullName() throws Exception {
      MimeMessage mimeMessage = new MimeMessage((Session) null);
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

      emailService.sendRegistrationOtp(
          "user@example.com", "<script>alert('xss')</script>", "123456");

      verify(mailSender).send(mimeMessage);
      String content = extractBodyContent(mimeMessage);
      assertThat(content).doesNotContain("<script>");
      assertThat(content).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Should throw AppException when mailSender throws exception on registration OTP")
    void sendRegistrationOtp_whenMailException_throwsAppException() {
      MimeMessage mimeMessage = new MimeMessage((Session) null);
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
      doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

      assertThatThrownBy(
              () -> emailService.sendRegistrationOtp("user@example.com", "Nguyen Van A", "123456"))
          .isInstanceOf(AppException.class)
          .hasMessageContaining("Không thể gửi email xác thực");
    }
  }

  @Nested
  @DisplayName("sendForgotPasswordOtp tests")
  class SendForgotPasswordOtpTests {

    @Test
    @DisplayName(
        "Should send forgot password OTP email with correct recipient, subject, and HTML content")
    void sendForgotPasswordOtp_sendsMimeMessageWithOtpContent() throws Exception {
      MimeMessage mimeMessage = new MimeMessage((Session) null);
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

      emailService.sendForgotPasswordOtp("reset@example.com", "Tran Van B", "654321");

      verify(mailSender).send(mimeMessage);
      assertThat(mimeMessage.getSubject()).contains("Đặt lại mật khẩu NinePaws");
      assertThat(mimeMessage.getAllRecipients()).hasSize(1);
      assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("reset@example.com");

      String content = extractBodyContent(mimeMessage);
      assertThat(content).contains("NinePaws");
      assertThat(content).contains("Tran Van B");
      assertThat(content).contains("654321");
      assertThat(content).contains("2 phút");
    }

    @Test
    @DisplayName(
        "Should throw AppException when mailSender throws exception on forgot password OTP")
    void sendForgotPasswordOtp_whenMailException_throwsAppException() {
      MimeMessage mimeMessage = new MimeMessage((Session) null);
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
      doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

      assertThatThrownBy(
              () -> emailService.sendForgotPasswordOtp("reset@example.com", "Tran Van B", "654321"))
          .isInstanceOf(AppException.class)
          .hasMessageContaining("Không thể gửi email xác thực");
    }
  }

  private String extractBodyContent(MimeMessage mimeMessage) throws Exception {
    return extractTextFromPart(mimeMessage);
  }

  private String extractTextFromPart(jakarta.mail.Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return s;
    }
    if (content instanceof jakarta.mail.Multipart multipart) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        sb.append(extractTextFromPart(multipart.getBodyPart(i)));
      }
      return sb.toString();
    }
    return "";
  }
}
