package interview.guide.modules.invitation.service;

import interview.guide.modules.invitation.config.InvitationProperties;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.engineer.model.EngineerEntity;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 面试邀约邮件发送
 *
 * 未配置 spring.mail.host 时以日志形式模拟发送（demo-first），
 * 配置后自动切换为真实 SMTP 投递
 */
@Slf4j
@Service
public class InvitationMailService {

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final InvitationProperties properties;

  public InvitationMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
      InvitationProperties properties) {
    this.mailSenderProvider = mailSenderProvider;
    this.properties = properties;
  }

  public boolean isRealMailConfigured() {
    return mailSenderProvider.getIfAvailable() != null;
  }

  public void sendInvitationMail(InterviewInvitationEntity invitation,
      EngineerEntity engineer) {
    String confirmUrl = properties.getFrontendBaseUrl() + "/invitation/" + invitation.getToken();
    String subject = "【面试邀约】" + resolvePosition(invitation) + " - 请确认参加";
    String body = buildBody(engineer.getName(), invitation, confirmUrl);

    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    if (sender == null) {
      log.info("[MockMail] 未配置 SMTP，模拟发送邀约邮件: to={}, subject={}, confirmUrl={}",
          invitation.getInvitedEmail(), subject, confirmUrl);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.getMailFromName());
    message.setTo(invitation.getInvitedEmail());
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
    log.info("面试邀约邮件已发送: to={}, invitationId={}", invitation.getInvitedEmail(),
        invitation.getId());
  }

  private String resolvePosition(InterviewInvitationEntity invitation) {
    return invitation.getPosition() != null ? invitation.getPosition() : "面试邀约";
  }

  private String buildBody(String engineerName, InterviewInvitationEntity invitation,
      String confirmUrl) {
    return engineerName + " 您好：\n\n"
        + "您已通过简历初筛，现邀约您参加「" + resolvePosition(invitation) + "」岗位的线上面试。\n\n"
        + "预约时间：" + invitation.getScheduledTime().format(TIME_FORMAT) + "\n"
        + "面试形式：线上视频面试（请提前准备摄像头与麦克风，并保持网络畅通）\n\n"
        + "请点击以下链接确认参加：\n" + confirmUrl + "\n\n"
        + "如时间冲突，请回复邮件与我们联系。\n\n"
        + properties.getMailFromName();
  }
}
