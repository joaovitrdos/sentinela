package br.com.sentinela.controller;

import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.infra.security.ErrorResponseWriter;
import br.com.sentinela.infra.security.SecurityConfig;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.service.AuthService;
import br.com.sentinela.service.CopernicusService;
import br.com.sentinela.service.FieldActionService;
import br.com.sentinela.service.PlotService;
import br.com.sentinela.service.TeamService;
import br.com.sentinela.service.PasswordRecoveryService;
import br.com.sentinela.service.PropertyService;
import br.com.sentinela.service.PropertyActivityService;
import br.com.sentinela.service.ReportService;
import br.com.sentinela.service.RainService;
import br.com.sentinela.service.ReferenceLevelService;
import br.com.sentinela.service.RiverService;
import br.com.sentinela.service.StationService;
import br.com.sentinela.service.UserService;
import br.com.sentinela.service.WeatherService;
import br.com.sentinela.service.MaintenanceService;
import br.com.sentinela.dto.response.SystemStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/** Camada web com a seguranca de verdade (JWT, filtros, handlers de erro) e os servicos mockados. */
@WebMvcTest
@Import({SecurityConfig.class, TokenService.class, ErrorResponseWriter.class})
@TestPropertySource(properties = {
        "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "sentinela.security.rate-limit.enabled=false"
})
abstract class WebLayerTestSupport {

    protected static final String UNAUTHORIZED = "Autenticação necessária para acessar este recurso";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TokenService tokenService;

    @MockitoBean
    protected UserRepository userRepository;

    @MockitoBean
    protected AuthService authService;

    @MockitoBean
    protected PasswordRecoveryService passwordRecoveryService;

    @MockitoBean
    protected UserService userService;

    @MockitoBean
    protected PropertyService propertyService;

    @MockitoBean
    protected PropertyActivityService propertyActivityService;

    @MockitoBean
    protected ReportService reportService;

    @MockitoBean
    protected CopernicusService copernicusService;

    @MockitoBean
    protected RainService rainService;

    @MockitoBean
    protected ReferenceLevelService referenceLevelService;

    @MockitoBean
    protected StationService stationService;

    @MockitoBean
    protected RiverService riverService;

    @MockitoBean
    protected WeatherService weatherService;

    @MockitoBean
    protected MaintenanceService maintenanceService;

    @MockitoBean
    protected FieldActionService fieldActionService;

    @MockitoBean
    protected PlotService plotService;

    @MockitoBean
    protected TeamService teamService;

    @BeforeEach
    void systemIsAvailable() {
        when(maintenanceService.getStatus()).thenReturn(new SystemStatusDto(false, "test", null, null, "America/Sao_Paulo"));
    }

    protected User user() {
        return User.builder()
                .userId(UUID.randomUUID())
                .name("Usuário")
                .email("qa@example.com")
                .password("hash")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();
    }

    /** Token valido para o usuario; o filtro de seguranca o encontra pelo ID imutavel. */
    protected String bearer(User user) {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        return "Bearer " + tokenService.generateToken(user);
    }
}
