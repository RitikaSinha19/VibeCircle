package com.vibecircle.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenUtilTest {

    @Test
    void sha256MatchesTheKnownTestVector() {
        assertThat(TokenUtil.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashIsAlways64HexCharactersWhichFitsTheCharColumn() {
        assertThat(TokenUtil.sha256Hex("")).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenUtil.sha256Hex("x".repeat(10_000))).hasSize(64);
    }

    @Test
    void generatedTokensAreUrlSafeLongAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = TokenUtil.newToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");    // 256 bits, base64url, no padding
            assertThat(seen.add(token)).as("duplicate token").isTrue();
        }
    }
}
