package br.com.sentinela.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailFactoryTest {
    private final EmailFactory factory = new EmailFactory("https://sentinela.example/");

    @Test
    void allMessagesUseBrandedLayout() {
        List<EmailMessage> messages = List.of(
                factory.createEmailVerification("ana@example.com", "123456"),
                factory.createEmailChange("ana@example.com", "123456"),
                factory.createPasswordRecovery("ana@example.com", "123456"),
                factory.createWelcomeEmail("ana@example.com", "Ana"),
                factory.createTeamInvitation("ana@example.com", "Equipe Norte", "Fazenda A", "João", "https://sentinela.example/convite")
        );

        assertThat(messages).allSatisfy(message -> {
            assertThat(message.isHtml()).isTrue();
            assertThat(message.getBody()).contains("cid:sentinela-mark", "<html lang=\"pt-BR\">", "Sentinela");
        });
    }

    @Test
    void invitationEscapesNamesAndIncludesCopyableLink() {
        String link = "https://sentinela.example/settings?tab=team&invite=abc";
        String html = factory.createTeamInvitation("ana@example.com", "Equipe <Norte>", "Fazenda A", "<script>", link).getBody();

        assertThat(html).contains("Equipe &lt;Norte&gt;", "&lt;script&gt;", "Aceitar convite", link.replace("&", "&amp;"));
        assertThat(html).doesNotContain("<script>");
    }
}
