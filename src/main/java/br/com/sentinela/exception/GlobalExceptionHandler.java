package br.com.sentinela.exception;

import br.com.sentinela.util.LogUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 400 Bad Request

    @ExceptionHandler(InvalidPropertyGeometryException.class)
    public ResponseEntity<String> invalidPropertyGeometryHandler(InvalidPropertyGeometryException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationDataException.class)
    public ResponseEntity<String> invalidRegistrationDataHandler(InvalidRegistrationDataException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<String> invalidVerificationCodeHandler(InvalidVerificationCodeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(ExpiredVerificationCodeException.class)
    public ResponseEntity<String> expiredVerificationCodeHandler(ExpiredVerificationCodeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    // 401 Unauthorized

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> invalidCredentialsHandler(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    @ExceptionHandler(IncorrectPasswordException.class)
    public ResponseEntity<String> incorrectPasswordHandler(IncorrectPasswordException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<String> invalidGoogleTokenHandler(InvalidGoogleTokenException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    @ExceptionHandler(GoogleEmailNotVerifiedException.class)
    public ResponseEntity<String> googleEmailNotVerifiedHandler(GoogleEmailNotVerifiedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    // 403 Forbidden

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<String> emailNotVerifiedHandler(EmailNotVerifiedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
    }

    // 404 Not Found

    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<String> propertyNotFoundHandler(PropertyNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> userNotFoundHandler(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    // 409 Conflict

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<String> emailAlreadyRegisteredHandler(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(GoogleAccountWithoutPasswordException.class)
    public ResponseEntity<String> googleAccountWithoutPasswordHandler(GoogleAccountWithoutPasswordException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(GoogleAccountEmailChangeException.class)
    public ResponseEntity<String> googleAccountEmailChangeHandler(GoogleAccountEmailChangeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    // 429 Too Many Requests

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<String> tooManyLoginAttemptsHandler(TooManyLoginAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(exception.getMessage());
    }

    @ExceptionHandler(TooManyCodeRequestsException.class)
    public ResponseEntity<String> tooManyCodeRequestsHandler(TooManyCodeRequestsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(exception.getMessage());
    }

    @ExceptionHandler(TooManyCodeAttemptsException.class)
    public ResponseEntity<String> tooManyCodeAttemptsHandler(TooManyCodeAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(exception.getMessage());
    }

    // 502 Bad Gateway

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<String> emailSendHandler(EmailSendException exception) {
        LogUtil.error("Falha no envio de e-mail", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
    }

    // 503 Service Unavailable

    @ExceptionHandler(GoogleAuthUnavailableException.class)
    public ResponseEntity<String> googleAuthUnavailableHandler(GoogleAuthUnavailableException exception) {
        LogUtil.warn("Login com Google indisponível", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
    }

    @ExceptionHandler(SnirhUnavailableException.class)
    public ResponseEntity<String> snirhUnavailableHandler(SnirhUnavailableException exception) {
        LogUtil.warn("SNIRH indisponível", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Os dados da ANA estão temporariamente indisponíveis. Tente novamente em instantes");
    }

    // 502 Bad Gateway (resposta invalida do SNIRH)

    @ExceptionHandler(SnirhApiException.class)
    public ResponseEntity<String> snirhApiHandler(SnirhApiException exception) {
        LogUtil.error("Resposta inválida do SNIRH", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Os dados da ANA vieram com erro. Tente novamente mais tarde");
    }

    @ExceptionHandler(WeatherApiUnavailableException.class)
    public ResponseEntity<String> weatherApiUnavailableHandler(WeatherApiUnavailableException exception) {
        LogUtil.warn("WeatherAPI indisponível");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
    }

    // 500 Internal Server Error

    @ExceptionHandler(UserSaveException.class)
    public ResponseEntity<String> userSaveHandler(UserSaveException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler(UserDeleteException.class)
    public ResponseEntity<String> userDeleteHandler(UserDeleteException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler(VerificationCodeSaveException.class)
    public ResponseEntity<String> verificationCodeSaveHandler(VerificationCodeSaveException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<String> reportGenerationHandler(ReportGenerationException exception) {
        LogUtil.error("Falha ao gerar relatório Jasper", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Não foi possível gerar o relatório");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> responseStatusHandler(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(exception.getReason() == null ? "Não foi possível processar a requisição" : exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unexpectedHandler(Exception exception) {
        LogUtil.error("Erro não tratado", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Erro inesperado ao processar a requisição");
    }

    // Erros de entrada tratados pelo Spring MVC

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Corpo da requisição ausente ou malformado");
    }
}
