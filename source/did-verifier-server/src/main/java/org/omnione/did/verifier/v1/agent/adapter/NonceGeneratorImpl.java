package org.omnione.did.verifier.v1.agent.adapter;

import org.omnione.did.base.util.BaseCryptoUtil;
import org.omnione.did.base.util.BaseMultibaseUtil;
import org.omnione.did.crypto.enums.MultiBaseType;
import org.omnione.did.verifier.v1.provider.NonceGenerator;
import org.springframework.stereotype.Component;

/**
 * NonceGenerator 구현체
 * BaseCryptoUtil을 활용한 Nonce 생성
 */
@Component
public class NonceGeneratorImpl implements NonceGenerator {

    /**
     * 지정된 길이의 Nonce 생성
     *
     * @param length Nonce 길이 (바이트 단위)
     * @return 생성된 Nonce (Base64 인코딩)
     */
    @Override
    public String generateNonce(int length) {
        byte[] nonceBytes = BaseCryptoUtil.generateNonce(length);
        return BaseMultibaseUtil.encode(nonceBytes, MultiBaseType.base64);
    }

    /**
     * 기본 길이(16바이트)의 Nonce 생성
     *
     * @return 생성된 Nonce (Base64 인코딩)
     */
    @Override
    public String generateNonce() {
        return generateNonce(16);
    }
}
