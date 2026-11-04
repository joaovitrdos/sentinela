package br.com.sentinela.service;

import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    private final EmailSenderService emailSenderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailRequested(EmailRequestedEvent event) {
        try {
            emailSenderService.send(event.message(), event.user());
        } catch (RuntimeException ex) {
            LogUtil.error("Falha ao enviar o e-mail \"" + event.message().getSubject() + "\"", ex);
        }
    }
}
