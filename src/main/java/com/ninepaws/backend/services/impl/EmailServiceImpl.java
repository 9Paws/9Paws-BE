package com.ninepaws.backend.services.impl;

import com.ninepaws.backend.exceptions.AppException;
import com.ninepaws.backend.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:no-reply@ninepaws.com}")
  private String fromEmail;

  @Override
  @Async
  public void sendRegistrationOtp(String toEmail, String fullName, String otp) {
    log.info("Sending registration OTP email to recipient: {}", toEmail);
    String subject = "NinePaws - Đăng ký tài khoản NinePaws";
    String title = "Xác thực đăng ký tài khoản";
    String messageBody =
        "Cảm ơn bạn đã lựa chọn NinePaws. Dưới đây là mã xác thực OTP dùng để hoàn tất đăng ký tài khoản của bạn:";

    String htmlContent = buildOtpHtmlContent(fullName, title, messageBody, otp);
    sendHtmlEmail(toEmail, subject, htmlContent);
  }

  @Override
  @Async
  public void sendForgotPasswordOtp(String toEmail, String fullName, String otp) {
    log.info("Sending forgot password OTP email to recipient: {}", toEmail);
    String subject = "NinePaws - Đặt lại mật khẩu NinePaws";
    String title = "Yêu cầu đặt lại mật khẩu";
    String messageBody =
        "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản NinePaws của bạn. Dưới đây là mã xác thực OTP:";

    String htmlContent = buildOtpHtmlContent(fullName, title, messageBody, otp);
    sendHtmlEmail(toEmail, subject, htmlContent);
  }

  private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

      try {
        helper.setFrom(fromEmail, "NinePaws");
      } catch (UnsupportedEncodingException e) {
        log.warn("Failed to set personal name on sender, falling back to raw address", e);
        helper.setFrom(fromEmail);
      }

      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Successfully sent email to recipient: {}", toEmail);
    } catch (MessagingException | RuntimeException e) {
      log.error("Failed to send email to recipient: {}", toEmail, e);
      throw new AppException(
          "Không thể gửi email xác thực. Vui lòng thử lại sau.",
          HttpStatus.INTERNAL_SERVER_ERROR,
          e);
    }
  }

  private String buildOtpHtmlContent(
      String fullName, String title, String messageBody, String otp) {
    String rawGreetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";
    String greetingName = HtmlUtils.htmlEscape(rawGreetingName);
    return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>%s</title>
          <style>
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
              background-color: #F8F9FA;
              margin: 0;
              padding: 0;
              color: #212529;
            }
            .container {
              max-width: 600px;
              margin: 30px auto;
              background-color: #FFFFFF;
              border-radius: 12px;
              overflow: hidden;
              box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
            }
            .header {
              background: linear-gradient(135deg, #FF6B6B 0%%, #FF8E53 100%%);
              padding: 32px 24px;
              text-align: center;
              color: #FFFFFF;
            }
            .header h1 {
              margin: 0;
              font-size: 28px;
              font-weight: 700;
              letter-spacing: 1px;
            }
            .content {
              padding: 36px 32px;
            }
            .greeting {
              font-size: 18px;
              font-weight: 600;
              margin-bottom: 16px;
            }
            .description {
              font-size: 15px;
              line-height: 1.6;
              color: #495057;
              margin-bottom: 24px;
            }
            .otp-container {
              background-color: #FFF4ED;
              border: 2px dashed #FF8E53;
              border-radius: 8px;
              padding: 20px;
              text-align: center;
              margin: 24px 0;
            }
            .otp-code {
              font-size: 36px;
              font-weight: 800;
              letter-spacing: 8px;
              color: #E64A19;
            }
            .notice {
              font-size: 14px;
              color: #6C757D;
              line-height: 1.5;
              margin-top: 16px;
            }
            .footer {
              background-color: #F8F9FA;
              padding: 20px 32px;
              text-align: center;
              font-size: 12px;
              color: #868E96;
              border-top: 1px solid #ECEFF1;
            }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">
              <h1>🐾 NinePaws</h1>
            </div>
            <div class="content">
              <div class="greeting">Xin chào %s,</div>
              <div class="description">%s</div>
              <div class="otp-container">
                <div class="otp-code"><strong>%s</strong></div>
              </div>
              <div class="notice">
                ⏰ Mã OTP này chỉ có hiệu lực trong vòng <strong>2 phút</strong>.<br>
                🔒 <strong>Bảo mật:</strong> Tuyệt đối không chia sẻ mã xác thực này cho bất kỳ ai, kể cả nhân viên NinePaws. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.
              </div>
            </div>
            <div class="footer">
              <p>&copy; 2026 NinePaws. Bản quyền thuộc về NinePaws Team.</p>
              <p>Email này được gửi tự động, vui lòng không phản hồi lại email này.</p>
            </div>
          </div>
        </body>
        </html>
        """
        .formatted(title, greetingName, messageBody, otp);
  }
}
