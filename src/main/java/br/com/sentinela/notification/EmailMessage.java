package br.com.sentinela.notification;

import br.com.sentinela.enums.TypeEmail;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailMessage {

    private final String recipient;
    private final String subject;
    private final String body;
    private final boolean html;
    private final TypeEmail type;
}
