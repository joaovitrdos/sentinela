package br.com.sentinela.notification;

import br.com.sentinela.entity.User;

public record EmailRequestedEvent(EmailMessage message, User user) {
}
