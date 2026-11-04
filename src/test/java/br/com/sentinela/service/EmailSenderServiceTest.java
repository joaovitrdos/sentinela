package br.com.sentinela.service;

import br.com.sentinela.entity.User;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.repository.EmailRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailSenderServiceTest {

    @Test
    void invitationContainsInlineLogoAndReadablePlainText() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailRepository audit = mock(EmailRepository.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        EmailSenderService service = new EmailSenderService(mailSender, audit, "sentinela@example.com", true);
        String link = "https://sentinela.example/settings?tab=team&invite=abc";

        service.send(new EmailFactory("https://sentinela.example")
                .createTeamInvitation("ana@example.com", "Equipe Norte", "Fazenda A", "João", link),
                User.builder().userId(UUID.randomUUID()).build());

        verify(mailSender).send(mime);
        mime.saveChanges();
        assertThat(findPart(mime, "image/png")).isNotNull();
        assertThat(findPart(mime, "text/html")).contains("cid:sentinela-mark");
        assertThat(findPart(mime, "text/plain")).contains(link, "Equipe Norte");
    }

    private String findPart(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return mimeType.startsWith("text/") ? part.getContent().toString() : part.getContentType();
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String found = findPart(child, mimeType);
                if (found != null) return found;
            }
        }
        return null;
    }
}
