package br.com.sentinela.service;

import br.com.sentinela.entity.User;
import br.com.sentinela.entity.VerificationCode;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.ExpiredVerificationCodeException;
import br.com.sentinela.exception.InvalidVerificationCodeException;
import br.com.sentinela.exception.VerificationCodeSaveException;
import br.com.sentinela.repository.VerificationCodeRepository;
import br.com.sentinela.util.EmailNormalizer;
import br.com.sentinela.util.LogUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VerificationCodeService {

    static final Duration CODE_TTL = Duration.ofMinutes(15);

    private static final int CODE_BOUND = 100_000_000;
    private static final String CODE_FORMAT = "%08d";

    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(
            VerificationCodeRepository verificationCodeRepository,
            PasswordEncoder passwordEncoder) {

        this.verificationCodeRepository = verificationCodeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String createCode(User user, CodePurpose purpose) {
        return createCode(user, purpose, null);
    }

    @Transactional
    public String createCode(User user, CodePurpose purpose, String targetEmail) {
        invalidatePendingCodes(user.getEmail(), purpose);

        String code = generateCode();
        VerificationCode verification = VerificationCode.builder()
                .user(user)
                .purpose(purpose)
                .targetEmail(targetEmail)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plus(CODE_TTL))
                .build();

        saveCode(verification);
        return code;
    }

    @Transactional(readOnly = true)
    public void validateCode(String code, String email, CodePurpose purpose) {
        findValidCode(code, email, purpose);
    }

    @Transactional
    public VerificationCode consumeCode(String code, String email, CodePurpose purpose) {
        VerificationCode verification = findValidCode(code, email, purpose);
        verification.setUsed(true);
        return saveCode(verification);
    }

    private VerificationCode findValidCode(String code, String email, CodePurpose purpose) {
        VerificationCode verification = verificationCodeRepository
                .findAllByUserEmailAndPurposeAndUsedFalse(EmailNormalizer.normalize(email), purpose).stream()
                .filter(candidate -> matches(code, candidate))
                .findFirst()
                .orElseThrow(InvalidVerificationCodeException::new);

        if (verification.isExpired(LocalDateTime.now())) {
            throw new ExpiredVerificationCodeException();
        }
        return verification;
    }

    private VerificationCode saveCode(VerificationCode verification) {
        try {
            return verificationCodeRepository.saveAndFlush(verification);
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao salvar o código de verificação", ex);
            throw new VerificationCodeSaveException(ex);
        }
    }

    private boolean matches(String code, VerificationCode candidate) {
        return code != null
                && candidate.getCodeHash() != null
                && passwordEncoder.matches(code, candidate.getCodeHash());
    }

    private void invalidatePendingCodes(String email, CodePurpose purpose) {
        List<VerificationCode> pending = verificationCodeRepository
                .findAllByUserEmailAndPurposeAndUsedFalse(EmailNormalizer.normalize(email), purpose);

        if (pending.isEmpty()) {
            return;
        }
        pending.forEach(code -> code.setUsed(true));

        try {
            verificationCodeRepository.saveAllAndFlush(pending);
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao invalidar os códigos de verificação pendentes", ex);
            throw new VerificationCodeSaveException(ex);
        }
    }

    private String generateCode() {
        return CODE_FORMAT.formatted(random.nextInt(CODE_BOUND));
    }
}
