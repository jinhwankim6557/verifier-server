package org.omnione.did.verifier.v1.protocol.service.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StatusListBitDecoderTest {

    private StatusListBitDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new StatusListBitDecoder();
    }

    @Test
    @DisplayName("bits=1, idx=0 → VALID(0)")
    void bits1_idx0_valid() throws Exception {
        // byte[0] = 0b00000000 → 모두 VALID
        String lst = StatusListBitDecoder.compress(new byte[]{0x00, 0x00});
        assertThat(decoder.extract(lst, 1, 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("bits=1, idx=0 → INVALID(1)")
    void bits1_idx0_invalid() throws Exception {
        // byte[0] = 0b00000001 → idx=0 이 INVALID
        String lst = StatusListBitDecoder.compress(new byte[]{0x01, 0x00});
        assertThat(decoder.extract(lst, 1, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("bits=1, idx=7 → INVALID(1)")
    void bits1_idx7_invalid() throws Exception {
        // byte[0] = 0b10000000 = 0x80 → idx=7 이 INVALID
        String lst = StatusListBitDecoder.compress(new byte[]{(byte) 0x80, 0x00});
        assertThat(decoder.extract(lst, 1, 7)).isEqualTo(1);
    }

    @Test
    @DisplayName("bits=2, idx=0 → SUSPENDED(2)")
    void bits2_idx0_suspended() throws Exception {
        // bits=2: idx=0 → bit_position=0, byte[0] bits[1:0] = 0b00000010 = 0x02
        String lst = StatusListBitDecoder.compress(new byte[]{0x02, 0x00});
        assertThat(decoder.extract(lst, 2, 0)).isEqualTo(2);
    }

    @Test
    @DisplayName("bits=2, idx=3 → INVALID(1)")
    void bits2_idx3_invalid() throws Exception {
        // bits=2: idx=3 → bit_position=6, byte[0] bits[7:6] = 0b01000000 = 0x40
        String lst = StatusListBitDecoder.compress(new byte[]{0x40, 0x00});
        assertThat(decoder.extract(lst, 2, 3)).isEqualTo(1);
    }

    @Test
    @DisplayName("idx가 범위 초과하면 예외")
    void idx_out_of_bounds_throws() {
        String lst = StatusListBitDecoder.compress(new byte[]{0x00}); // 8개만 (bits=1)
        assertThatThrownBy(() -> decoder.extract(lst, 1, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of bounds");
    }
}
