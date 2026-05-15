package org.omnione.did.verifier.v1.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.VpSubmit;
import org.omnione.did.base.db.repository.VpSubmitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * VP 제출 감사(audit) 전용 저장 서비스.
 *
 * 제출 시도에 대한 VpSubmit 기록은 호출자 트랜잭션이 롤백되더라도
 * 반드시 커밋되어야 부인방지/이력 증적으로서의 가치가 유지된다.
 * 따라서 모든 저장 메서드는 REQUIRES_NEW 로 별도 트랜잭션에서 수행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VpSubmitAuditService {

    private final VpSubmitRepository vpSubmitRepository;

    /**
     * 제출 성공 증적 저장. 이미 같은 transactionId 로 저장된 행이 있으면 덮어쓰지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long transactionId, String vp, String holderDid, String format) {
        if (transactionId == null) {
            return;
        }
        try {
            if (vpSubmitRepository.findByTransactionId(transactionId) != null) {
                return;
            }
            vpSubmitRepository.save(VpSubmit.builder()
                    .transactionId(transactionId)
                    .vp(vp)
                    .holderDid(holderDid)
                    .format(format)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist VpSubmit success audit for transactionId {}", transactionId, e);
        }
    }

    /**
     * 제출 실패 증적 저장. vp / holderDid 는 실패 시점 복구 가능한 범위에서 전달하면 되고,
     * 불가능한 경우 null 허용.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long transactionId, String vp, String holderDid, String errorCode, String format) {
        if (transactionId == null) {
            return;
        }
        try {
            if (vpSubmitRepository.findByTransactionId(transactionId) != null) {
                return;
            }
            vpSubmitRepository.save(VpSubmit.builder()
                    .transactionId(transactionId)
                    .vp(vp)
                    .holderDid(holderDid)
                    .errorCode(errorCode)
                    .format(format)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist VpSubmit failure audit for transactionId {}", transactionId, e);
        }
    }
}
