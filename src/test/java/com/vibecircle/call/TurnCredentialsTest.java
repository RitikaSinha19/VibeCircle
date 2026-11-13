package com.vibecircle.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurnCredentialsTest {

    @Test
    void matchesAnIndependentlyComputedHmacSha1() {
        // python: base64(hmac.new(b"secret", b"1700000000:42", sha1).digest())
        TurnCredentials.Credential c = TurnCredentials.generate("secret", 42, 1_700_000_000L);
        assertThat(c.username()).isEqualTo("1700000000:42");
        assertThat(c.credential()).isEqualTo("ZzfPuPkrzUOsnm5YISnTiKDnXWo=");
    }

    @Test
    void isDeterministicAndDependsOnEverything() {
        var base = TurnCredentials.generate("secret", 42, 1_700_000_000L);
        assertThat(TurnCredentials.generate("secret", 42, 1_700_000_000L)).isEqualTo(base);
        assertThat(TurnCredentials.generate("other", 42, 1_700_000_000L).credential()).isNotEqualTo(base.credential());
        assertThat(TurnCredentials.generate("secret", 43, 1_700_000_000L).credential()).isNotEqualTo(base.credential());
        assertThat(TurnCredentials.generate("secret", 42, 1_700_000_001L).credential()).isNotEqualTo(base.credential());
    }
}
