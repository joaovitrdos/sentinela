package br.com.sentinela.notification;

import br.com.sentinela.enums.TypeEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailFactory {
    private static final String APP_NAME = "Sentinela";
    private static final long CODE_TTL_MINUTES = 15;
    private final String webBaseUrl;

    public EmailFactory(@Value("${sentinela.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    public EmailMessage createTeamInvitation(String recipient, String teamTitle, String propertyName, String inviterName, String link) {
        String content = paragraph(escape(inviterName) + " convidou você para participar da equipe <strong>"
                + escape(teamTitle) + "</strong> na propriedade <strong>" + escape(propertyName) + "</strong>.")
                + button("Aceitar convite", link) + fallbackLink(link)
                + note("Este convite expira em 7 dias. Se você não esperava recebê-lo, pode ignorar esta mensagem.");
        return message(recipient, "Convite para equipe - " + APP_NAME, TypeEmail.TEAM_INVITATION,
                layout("Você recebeu um convite para colaborar no Sentinela.", "Um convite para colaborar", content));
    }

    public EmailMessage createEmailVerification(String recipient, String code) {
        return codeEmail(recipient, TypeEmail.EMAIL_VERIFICATION,
                "Confirme seu e-mail", "Use este código para confirmar seu cadastro.", code);
    }

    public EmailMessage createEmailChange(String recipient, String code) {
        return codeEmail(recipient, TypeEmail.EMAIL_CHANGE,
                "Confirme seu novo e-mail", "Use este código para confirmar a troca do e-mail da sua conta.", code);
    }

    public EmailMessage createPasswordRecovery(String recipient, String code) {
        return codeEmail(recipient, TypeEmail.REFRESH_PASSWORD,
                "Redefina sua senha", "Use este código para redefinir sua senha.", code);
    }

    public EmailMessage createWelcomeEmail(String recipient, String name) {
        String greeting = name == null || name.isBlank() ? "Olá!" : "Olá, " + escape(name.trim()) + "!";
        String content = paragraph(greeting)
                + paragraph("Sua conta está pronta. Acompanhe suas propriedades, atividades e informações em um só lugar.")
                + button("Acessar o Sentinela", webBaseUrl) + fallbackLink(webBaseUrl);
        return message(recipient, "Bem-vindo ao " + APP_NAME, TypeEmail.NEW_USER,
                layout("Sua conta no Sentinela está pronta.", "Boas-vindas ao Sentinela", content));
    }

    private EmailMessage codeEmail(String recipient, TypeEmail type, String subject, String intro, String code) {
        String content = paragraph(escape(intro))
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" "
                + "style=\"margin:8px 0 24px;border:1px solid #d7e5f5;border-radius:12px;background:#f6f9fd\">"
                + "<tr><td align=\"center\" style=\"padding:22px 12px\">"
                + "<span style=\"font-family:Arial,Helvetica,sans-serif;font-size:30px;line-height:38px;letter-spacing:7px;font-weight:700;color:#15243a\">"
                + escape(code) + "</span></td></tr></table>"
                + note("O código expira em " + CODE_TTL_MINUTES + " minutos. Não compartilhe este código. "
                + "Se você não fez esta solicitação, pode ignorar este e-mail.");
        return message(recipient, subject + " - " + APP_NAME, type,
                layout(subject + " no Sentinela.", subject, content));
    }

    private EmailMessage message(String recipient, String subject, TypeEmail type, String body) {
        return EmailMessage.builder().recipient(recipient).subject(subject).type(type).html(true).body(body).build();
    }

    private String paragraph(String content) {
        return "<p style=\"margin:0 0 20px;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:25px;color:#344054\">"
                + content + "</p>";
    }

    private String button(String label, String link) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:26px 0 18px\">"
                + "<tr><td bgcolor=\"#0066cc\" style=\"border-radius:999px;background:#0066cc\">"
                + "<a href=\"" + escape(link) + "\" style=\"display:inline-block;padding:13px 24px;border-radius:999px;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:700;line-height:20px;color:#ffffff;text-decoration:none\">"
                + escape(label) + "</a></td></tr></table>";
    }

    private String fallbackLink(String link) {
        return "<p style=\"margin:0 0 22px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:19px;color:#667085\">"
                + "Se o botão não abrir, copie este endereço:<br><a href=\"" + escape(link)
                + "\" style=\"color:#0066cc;text-decoration:underline;word-break:break-all\">"
                + escape(link) + "</a></p>";
    }

    private String note(String content) {
        return "<p style=\"margin:22px 0 0;padding:16px 0 0;border-top:1px solid #eaecf0;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:21px;color:#667085\">"
                + escape(content) + "</p>";
    }

    private String layout(String preheader, String title, String content) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f5f5f7;color:#1d1d1f">
                <div class="preheader" style="display:none!important;visibility:hidden;opacity:0;height:0;width:0;overflow:hidden;color:transparent">%s</div>
                <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background:#f5f5f7;border-collapse:collapse">
                  <tr><td align="center" style="padding:32px 16px 40px">
                    <table role="presentation" cellpadding="0" cellspacing="0" width="560" style="width:100%%;max-width:560px;border-collapse:separate;background:#ffffff;border:1px solid #e5e7eb;border-radius:18px">
                      <tr><td style="padding:32px 36px 26px;border-bottom:1px solid #eaecf0">
                        <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                          <td style="padding-right:12px"><img src="cid:sentinela-mark" width="44" height="44" alt="Sentinela" style="display:block;width:44px;height:44px;border:0;border-radius:11px"></td>
                          <td style="font-family:Arial,Helvetica,sans-serif;font-size:18px;line-height:24px;font-weight:700;letter-spacing:-0.3px;color:#1d1d1f">Sentinela</td>
                        </tr></table>
                      </td></tr>
                      <tr><td style="padding:36px 36px 38px">
                        <h1 style="margin:0 0 20px;font-family:Arial,Helvetica,sans-serif;font-size:28px;line-height:34px;font-weight:700;letter-spacing:-0.5px;color:#1d1d1f">%s</h1>
                        %s
                      </td></tr>
                    </table>
                    <p style="margin:22px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:19px;color:#667085">Mensagem automática do Sentinela. Não responda a este e-mail.</p>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(escape(preheader), escape(title), content);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
