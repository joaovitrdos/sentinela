package br.com.sentinela.repository;

import br.com.sentinela.entity.VerificationCode;
import br.com.sentinela.enums.CodePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    List<VerificationCode> findAllByUserEmailAndPurposeAndUsedFalse(String email, CodePurpose purpose);

}
