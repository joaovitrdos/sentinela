package br.com.sentinela.service;

import br.com.sentinela.dto.request.*;
import br.com.sentinela.dto.response.FieldActionResponseDto;
import br.com.sentinela.entity.FieldAction;
import br.com.sentinela.repository.FieldActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class FieldActionService {
    private final PropertyService propertyService;
    private final PlotService plotService;
    private final PropertyActivityService activityService;
    private final FieldActionRepository actionRepository;
    private final TeamService teamService;
    private final br.com.sentinela.repository.UserRepository users;

    @Transactional(readOnly = true)
    public List<FieldActionResponseDto> list(UUID ownerId, UUID propertyId) {
        propertyService.requireView(propertyId, ownerId);
        return actionRepository.findAllByProperty_PropertyIdOrderByDueDateAscCreatedAtAsc(propertyId)
                .stream().map(this::response).toList();
    }

    @Transactional
    public FieldActionResponseDto create(UUID ownerId, UUID propertyId, FieldActionRequestDto request) {
        var property = propertyService.requireEdit(propertyId, ownerId);
        var plot = request.plotId() == null ? null : plotService.requirePlot(propertyId, ownerId, request.plotId());
        if (request.assigneeUserId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um membro responsável");
        var assignee = teamService.requireActiveMember(property, request.assigneeUserId());
        FieldAction action = FieldAction.builder().property(property).plot(plot).kind(request.kind())
                .title(request.title().trim()).assignee(assignee)
                .responsibleName(assignee.getName())
                .dueDate(request.dueDate()).status("OPEN")
                .sourceNote(request.sourceNote() == null || request.sourceNote().isBlank() ? null : request.sourceNote().trim())
                .build();
        return response(actionRepository.saveAndFlush(action));
    }

    @Transactional
    public FieldActionResponseDto complete(UUID ownerId, UUID propertyId, UUID actionId, CompleteActionRequestDto request) {
        propertyService.requireEdit(propertyId, ownerId);
        FieldAction action = actionRepository.findByActionIdAndProperty_PropertyId(actionId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação não encontrada"));
        if (!"OPEN".equals(action.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ação já concluída");
        }
        if (!"IRRIGATION".equals(action.getKind()) && request.amountMm() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lâmina só é permitida em irrigação");
        }
        String note = request.completionNote().trim();
        action.setStatus("DONE");
        action.setCompletionNote(note);
        action.setCompletedAt(LocalDateTime.now());
        action.setCompletedBy(users.getReferenceById(ownerId));
        // Grava a acao (com @Version) antes de criar a atividade: numa conclusao concorrente a segunda
        // transacao falha aqui com conflito de versao (409) e nao gera atividade duplicada.
        FieldAction saved = actionRepository.saveAndFlush(action);
        activityService.create(ownerId, propertyId, new PropertyActivityRequestDto(action.getKind(),
                LocalDate.now(), null, note, request.amountMm(),
                action.getPlot() == null ? null : action.getPlot().getPlotId()));
        return response(saved);
    }

    @Transactional
    public FieldActionResponseDto reassign(UUID userId, UUID propertyId, UUID actionId, UUID assigneeUserId) {
        var property = propertyService.requireEdit(propertyId, userId);
        FieldAction action = actionRepository.findByActionIdAndProperty_PropertyId(actionId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação não encontrada"));
        if (!"OPEN".equals(action.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ação já concluída");
        var assignee = teamService.requireActiveMember(property, assigneeUserId);
        action.setAssignee(assignee);
        action.setResponsibleName(assignee.getName());
        action.setReminderSentAt(null);
        return response(actionRepository.saveAndFlush(action));
    }

    @Transactional
    public void delete(UUID ownerId, UUID propertyId, UUID actionId) {
        propertyService.requireAdmin(propertyId, ownerId);
        FieldAction action = actionRepository.findByActionIdAndProperty_PropertyId(actionId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação não encontrada"));
        actionRepository.delete(action);
    }

    private FieldActionResponseDto response(FieldAction action) {
        return new FieldActionResponseDto(action.getActionId(), action.getPlot() == null ? null : action.getPlot().getPlotId(),
                action.getKind(), action.getTitle(), action.getResponsibleName(), action.getDueDate(), action.getStatus(),
                action.getSourceNote(), action.getCompletionNote(), action.getCompletedAt(), action.getCreatedAt(),
                action.getAssignee() == null ? null : action.getAssignee().getUserId(),
                action.getCompletedBy() == null ? null : action.getCompletedBy().getUserId(),
                action.getCompletedBy() == null ? null : action.getCompletedBy().getName());
    }
}
