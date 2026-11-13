package com.vibecircle.call;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-limited TURN credentials in coturn's "REST API" format (turnserver.conf: use-auth-secret).
 * username = "<expiry unix time>:<user id>", credential = base64(HMAC-SHA1(secret, username)).
 * The TURN server recomputes the HMAC, so no password is ever stored, and a leaked credential dies on its own.
 */
public final class TurnCredentials {

    public record Credential(String username, String credential) {
    }

    private TurnCredentials() {
    }

    public static Credential generate(String secret, long userId, long expiresAtEpochSeconds) {
        String username = expiresAtEpochSeconds + ":" + userId;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            return new Credential(username, credential);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is not available", e);
        }
    }
}
