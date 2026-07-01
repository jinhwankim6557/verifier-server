package org.omnione.did.verifier.v1.protocol.service.status;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@Component
public class StatusListBitDecoder {

    public int extract(String lst, int bits, int idx) throws Exception {
        byte[] compressed = Base64.getUrlDecoder().decode(padBase64(lst));
        byte[] bytes = decompress(compressed);

        int bitPosition = idx * bits;
        int byteIndex = bitPosition / 8;
        int bitOffset = bitPosition % 8;
        int mask = (1 << bits) - 1;

        if (byteIndex >= bytes.length) {
            throw new IllegalArgumentException(
                "Status list index " + idx + " is out of bounds (list size: " + (bytes.length * 8 / bits) + ")");
        }

        return (bytes[byteIndex] >> bitOffset) & mask;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = iis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }

    private String padBase64(String base64url) {
        int padding = (4 - base64url.length() % 4) % 4;
        return base64url + "=".repeat(padding);
    }
}
