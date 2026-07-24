package org.omnione.did.verifier.v1.protocol.service.status;

import lombok.RequiredArgsConstructor;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@Component
@RequiredArgsConstructor
public class StatusListBitDecoder {

    private static final Set<Integer> SUPPORTED_BITS = Set.of(1, 2, 4, 8);

    private final VerifierProperty verifierProperty;

    public int extract(String lst, int bits, int idx) throws Exception {
        if (!SUPPORTED_BITS.contains(bits)) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
        if (idx < 0) {
            // idx가 음수면 bitOffset도 음수가 되고, Java의 int 시프트는 시프트량을 &31로 감싸므로
            // (예: idx=-1, bits=2 → bitOffset=-2 → 실제로는 >>30) byteIndex 상한 체크를 우회한 채
            // 항상 0(VALID)을 반환하는 조용한 오탐이 발생한다. 배열 접근 전에 명시적으로 걸러낸다.
            throw new IllegalArgumentException("Status list index must not be negative: " + idx);
        }

        byte[] compressed = JwtPayloadUtils.decodeBase64Url(lst);
        byte[] bytes = decompress(compressed);

        int bitPosition = idx * bits;
        int byteIndex = bitPosition / 8;
        int bitOffset = bitPosition % 8;
        int mask = (1 << bits) - 1;

        if (byteIndex >= bytes.length) {
            throw new IllegalArgumentException(
                "Status list index " + idx + " is out of bounds (list size: " + (bytes.length * 8 / bits) + ")");
        }

        // bits는 위에서 {1,2,4,8}로 제한했으므로 idx*bits 범위는 항상 단일 바이트 안에 들어온다.
        // & 0xFF로 부호 확장을 먼저 제거해 Java의 signed byte 우측 시프트 문제를 방어적으로 차단한다.
        return ((bytes[byteIndex] & 0xFF) >> bitOffset) & mask;
    }

    // 테스트에서 lst 생성에 사용
    public static String compress(byte[] raw) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
                dos.write(raw);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decompress(byte[] compressed) throws Exception {
        long maxBytes = verifierProperty.getStatusList().getMaxDecompressedBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long total = 0;
        try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = iis.read(buf)) != -1) {
                total += len;
                if (total > maxBytes) {
                    // decompression bomb 방어: 압축 해제 도중 상한을 넘으면 즉시 중단한다.
                    throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
                }
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }
}
