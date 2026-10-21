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
          <meta http-equiv="X-UA-Compatible" content="IE=edge">
          <title>%s</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #F1F5F9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1E293B;">
          <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F1F5F9; padding: 40px 16px;">
            <tr>
              <td align="center">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #FFFFFF; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.03); border: 1px solid #E2E8F0;">
                  <tr>
                    <td style="background: linear-gradient(135deg, #4F46E5 0%%, #7C3AED 60%%, #9333EA 100%%); padding: 40px 32px; text-align: center;">
                      <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                          <td align="center">
                            <div style="display: inline-block; width: 56px; height: 56px; line-height: 56px; background-color: rgba(255, 255, 255, 0.18); border-radius: 16px; font-size: 28px; text-align: center; margin-bottom: 12px;">
                              🐾
                            </div>
                            <h1 style="margin: 0; color: #FFFFFF; font-size: 28px; font-weight: 800; letter-spacing: -0.5px; line-height: 1.2;">NinePaws</h1>
                            <p style="margin: 6px 0 0 0; color: rgba(255, 255, 255, 0.85); font-size: 13px; font-weight: 500; letter-spacing: 0.5px;">Quản lý tài chính cá nhân & Chia tiền thông minh</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding: 40px 36px 32px 36px;">
                      <h2 style="margin: 0 0 16px 0; color: #0F172A; font-size: 20px; font-weight: 700;">Xin chào %s,</h2>
                      <p style="margin: 0 0 24px 0; color: #475569; font-size: 15px; line-height: 1.6;">%s</p>
                      <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                        <tr>
                          <td style="background: linear-gradient(180deg, #F5F3FF 0%%, #EDE9FE 100%%); border: 2px dashed #818CF8; border-radius: 16px; padding: 28px 20px; text-align: center;">
                            <span style="display: block; color: #6D28D9; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; margin-bottom: 8px;">Mã xác thực của bạn</span>
                            <div style="font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace; font-size: 40px; font-weight: 800; letter-spacing: 10px; color: #4338CA; line-height: 1; padding-left: 10px;">
                              <strong>%s</strong>
                            </div>
                            <div style="display: inline-block; margin-top: 14px; padding: 4px 12px; background-color: rgba(109, 40, 217, 0.08); border-radius: 20px; color: #5B21B6; font-size: 12px; font-weight: 600;">
                              ⏱ Hiệu lực trong 2 phút
                            </div>
                          </td>
                        </tr>
                      </table>
                      <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-top: 24px; background-color: #FFFBEB; border: 1px solid #FDE68A; border-radius: 12px; padding: 14px 16px;">
                        <tr>
                          <td style="color: #92400E; font-size: 13px; line-height: 1.5;">
                            <strong style="color: #78350F;">🔒 Lưu ý bảo mật:</strong> Tuyệt đối không chia sẻ mã này cho bất kỳ ai, kể cả nhân viên NinePaws. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  <tr>
                    <td style="background-color: #F8FAFC; padding: 24px 36px; border-top: 1px solid #E2E8F0; text-align: center;">
                      <p style="margin: 0 0 6px 0; color: #64748B; font-size: 13px; font-weight: 600;">NinePaws Financial Assistant &middot; Đồng hành chi tiêu thông minh</p>
                      <p style="margin: 0; color: #94A3B8; font-size: 12px; line-height: 1.5;">Email này được gửi tự động từ hệ sinh thái NinePaws. Vui lòng không trả lời trực tiếp email này.<br>&copy; 2026 NinePaws. Tất cả các quyền được bảo lưu.</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """
        .formatted(title, greetingName, messageBody, otp);
  }
}
