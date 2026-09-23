package io.github.diegofranciscog.textrack.service.calc;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma y verifica tickets con HMAC-SHA-256 (RFC 2104). La clave vive solo en el servidor: un ticket impreso
 * o fotocopiado con otra cantidad u otro bulto no pasa la verificación.
 */
public final class TicketSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int TAG_BYTES = 16;

    private final SecretKeySpec key;
    private final String keyId;

    public TicketSigner(String secret, String keyId) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("La clave HMAC debe tener al menos 32 bytes");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.keyId = keyId;
    }

    public TicketPayload sign(UUID ticketId, String bundleCode, String operationCode, int quantity) {
        TicketPayload unsigned = new TicketPayload(keyId, ticketId, bundleCode, operationCode, quantity, "");
        return new TicketPayload(keyId, ticketId, bundleCode, operationCode, quantity, tag(unsigned.signedContent()));
    }

    public boolean verify(TicketPayload payload) {
        if (!keyId.equals(payload.keyId())) {
            return false;
        }
        byte[] expected = Base64.getUrlDecoder().decode(tag(payload.signedContent()));
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(payload.signature());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    public String keyId() {
        return keyId;
    }

    private String tag(String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] full = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, TAG_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }
}
