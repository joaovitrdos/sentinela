package br.com.sentinela.service;

import br.com.sentinela.entity.Email;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.StatusEmail;
import br.com.sentinela.exception.EmailSendException;
import br.com.sentinela.notification.EmailMessage;
import br.com.sentinela.repository.EmailRepository;
import br.com.sentinela.util.LogUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class EmailSenderService {

    private static final String PREHEADER = ".preheader";

    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;
    private final String from;
    private final boolean enabled;

    public EmailSenderService(
            JavaMailSender mailSender,
            EmailRepository emailRepository,
            @Value("${sentinela.mail.from:${spring.mail.username:}}") String from,
            @Value("${sentinela.mail.enabled:true}") boolean enabled) {

        this.mailSender = mailSender;
        this.emailRepository = emailRepository;
        this.from = from == null ? "" : from.trim();
        this.enabled = enabled;
    }

    public void send(EmailMessage emailMessage, User user) {
        Objects.requireNonNull(emailMessage, "emailMessage não pode ser nulo");
        Objects.requireNonNull(user, "user não pode ser nulo");

        String recipient = emailMessage.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("O destinatário do e-mail não pode ser vazio");
        }

        if (!enabled) {
            LogUtil.info("Envio de e-mail desabilitado (sentinela.mail.enabled=false). Assunto \"{0}\" ignorado",
                    emailMessage.getSubject());
            return;
        }

        Email emailEntity = new Email();
        emailEntity.setUserId(user.getUserId());
        emailEntity.setEmailFrom(this.from);
        emailEntity.setEmailTo(recipient.trim());
        emailEntity.setSubject(emailMessage.getSubject());
        emailEntity.setTypeEmail(emailMessage.getType());

        try {
            mailSender.send(buildMessage(recipient, emailMessage));
        } catch (MailException ex) {
            emailEntity.setStatusEmail(StatusEmail.ERROR);
            saveAudit(emailEntity);
            LogUtil.error("Não foi possível enviar o e-mail \"" + emailMessage.getSubject() + "\"", ex);
            throw new EmailSendException(ex);
        }

        emailEntity.setStatusEmail(StatusEmail.SENT);
        saveAudit(emailEntity);
        LogUtil.info("E-mail \"{0}\" enviado", emailMessage.getSubject());
    }

    private void saveAudit(Email emailEntity) {
        try {
            emailRepository.save(emailEntity);
        } catch (DataAccessException ex) {
            LogUtil.error("E-mail terminou como " + emailEntity.getStatusEmail()
                    + ", mas a auditoria não foi gravada", ex);
        }
    }

    private MimeMessage buildMessage(String recipient, EmailMessage emailMessage) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            if (!from.isEmpty()) {

                helper.setFrom(from);
            }
            helper.setTo(recipient.trim());
            helper.setSubject(emailMessage.getSubject());

            String body = emailMessage.getBody();
            if (emailMessage.isHtml()) {

                helper.setText(plainTextOf(body), body);
                if (body.contains("cid:sentinela-mark")) {
                    helper.addInline("sentinela-mark", new ClassPathResource("email/sentinela-mark.png"), "image/png");
                }
            } else {
                helper.setText(body, false);
            }
        } catch (MessagingException ex) {
            throw new EmailSendException(ex);
        }

        return message;
    }

    private String plainTextOf(String html) {
        Document document = Jsoup.parse(html);
        document.select(PREHEADER).remove();

        return document.body().wholeText()
                .replaceAll("[ \t]+", " ")
                .replaceAll(" ?\n ?", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
