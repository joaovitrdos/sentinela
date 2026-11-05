package br.com.sentinela.service;

import br.com.sentinela.dto.request.CompleteActionRequestDto;
import br.com.sentinela.dto.request.PropertyActivityRequestDto;
import br.com.sentinela.entity.FieldAction;
import br.com.sentinela.entity.Plot;
import br.com.sentinela.entity.Property;
import br.com.sentinela.repository.FieldActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FieldActionServiceTest {
    @Mock PropertyService propertyService;
    @Mock PlotService plotService;
    @Mock PropertyActivityService activityService;
    @Mock FieldActionRepository actionRepository;
    @Mock br.com.sentinela.repository.UserRepository users;
    @Mock TeamService teamService;
    @InjectMocks FieldActionService service;

    @Test
    void completingIrrigationRecordsWaterInTheSamePlot() {
        UUID ownerId = UUID.randomUUID(), propertyId = UUID.randomUUID(), plotId = UUID.randomUUID(), actionId = UUID.randomUUID();
        Plot plot = Plot.builder().plotId(plotId).build();
        FieldAction action = FieldAction.builder().actionId(actionId).property(Property.builder().propertyId(propertyId).build())
                .plot(plot).kind("IRRIGATION").title("Irrigar").responsibleName("Maria")
                .dueDate(LocalDate.now()).status("OPEN").build();
        when(actionRepository.findByActionIdAndProperty_PropertyId(actionId, propertyId)).thenReturn(Optional.of(action));
        when(actionRepository.saveAndFlush(action)).thenReturn(action);
        when(users.getReferenceById(ownerId)).thenReturn(br.com.sentinela.entity.User.builder().userId(ownerId).name("Responsável").build());

        var result = service.complete(ownerId, propertyId, actionId, new CompleteActionRequestDto("Aplicados 12 mm", 12.0));

        assertThat(result.status()).isEqualTo("DONE");
        verify(propertyService).requireEdit(propertyId, ownerId);
        verify(activityService).create(eq(ownerId), eq(propertyId), argThat((PropertyActivityRequestDto request) ->
                "IRRIGATION".equals(request.type()) && plotId.equals(request.plotId()) && request.amountMm() == 12.0));
    }

    @Test
    void cannotCompleteTheSameActionTwice() {
        UUID ownerId = UUID.randomUUID(), propertyId = UUID.randomUUID(), actionId = UUID.randomUUID();
        FieldAction action = FieldAction.builder().status("DONE").build();
        when(actionRepository.findByActionIdAndProperty_PropertyId(actionId, propertyId)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.complete(ownerId, propertyId, actionId, new CompleteActionRequestDto("Feito", null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(activityService);
    }

    @Test
    void assignsOnlyARealMemberOfTheProperty() {
        UUID actorId = UUID.randomUUID(), propertyId = UUID.randomUUID(), assigneeId = UUID.randomUUID();
        Property property = Property.builder().propertyId(propertyId).build();
        br.com.sentinela.entity.User member = br.com.sentinela.entity.User.builder()
                .userId(assigneeId).name("Ana Técnica").build();
        when(propertyService.requireEdit(propertyId, actorId)).thenReturn(property);
        when(teamService.requireActiveMember(property, assigneeId)).thenReturn(member);
        when(actionRepository.saveAndFlush(any(FieldAction.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.create(actorId, propertyId, new br.com.sentinela.dto.request.FieldActionRequestDto(
                "INSPECTION", "Vistoriar talhão", null, LocalDate.now().plusDays(1), null, null, assigneeId));

        assertThat(result.assigneeUserId()).isEqualTo(assigneeId);
        assertThat(result.responsibleName()).isEqualTo("Ana Técnica");
    }
}
