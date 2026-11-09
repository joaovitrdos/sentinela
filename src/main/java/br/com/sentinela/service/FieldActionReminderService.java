package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.entity.FieldAction;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.repository.FieldActionRepository;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service @RequiredArgsConstructor
public class FieldActionReminderService {
    private final FieldActionRepository actions;
    private final TeamService teams;
    private final EmailFactory emails;
    private final EmailSenderService sender;
    @Value("${sentinela.mail.enabled:true}") private boolean mailEnabled;

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void remind() {
        if (!mailEnabled) return;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        for (FieldAction action : actions.findAllByStatusAndReminderSentAtIsNullAndDueDateLessThanEqualAndAssigneeIsNotNull(
                "OPEN", today.plusDays(7))) {
            try {
                var property = action.getProperty();
                if (!Boolean.TRUE.equals(property.getActionReminderEnabled()) ||
                        action.getDueDate().isAfter(today.plusDays(property.getActionReminderDays()))) continue;
                if (Util.isNull(teams.role(action.getProperty(), action.getAssignee().getUserId()))) continue;
                var user = teams.requireActiveMember(action.getProperty(), action.getAssignee().getUserId());
                sender.send(emails.createFieldActionReminder(user.getEmail(), action.getProperty().getName(), action.getTitle(),
                        action.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))), user);
                action.setReminderSentAt(LocalDateTime.now());
                actions.save(action);
            } catch (RuntimeException ex) {
                LogUtil.error("Falha ao avisar o prazo da ação " + action.getActionId(), ex);
            }
        }
    }
}
