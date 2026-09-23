package io.github.diegofranciscog.textrack.service.calc;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contenido del QR de un ticket: {@code TT1.<keyId>.<ticketId>.<bundleCode>.<operationCode>.<qty>.<firma>}.
 *
 * <p>Lleva datos legibles (bulto, operación, cantidad) para que la app muestre el ticket sin conexión, y una
 * firma HMAC truncada a 128 bits (base64url, 22 caracteres) que solo el servidor puede verificar.</p>
 */
public record TicketPayload(String keyId, UUID ticketId, String bundleCode, String operationCode, int quantity,
                            String signature) {

    public static final String VERSION = "TT1";
    public static final int MAX_LENGTH = 300;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9]{1,10}");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9-]{1,40}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{22}");

    public String signedContent() {
        return String.join(".", VERSION, keyId, ticketId.toString(), bundleCode, operationCode,
                Integer.toString(quantity));
    }

    public String encode() {
        return signedContent() + "." + signature;
    }

    /** Parsea sin lanzar excepciones: cualquier contenido malformado devuelve vacío. */
    public static Optional<TicketPayload> parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        String[] parts = raw.trim().split("\\.", -1);
        if (parts.length != 7 || !VERSION.equals(parts[0]) || !KEY_ID.matcher(parts[1]).matches()
                || !CODE.matcher(parts[3]).matches() || !CODE.matcher(parts[4]).matches()
                || !SIGNATURE.matcher(parts[6]).matches()) {
            return Optional.empty();
        }
        try {
            UUID ticketId = UUID.fromString(parts[2]);
            if (!ticketId.toString().equals(parts[2].toLowerCase())) {
                return Optional.empty();
            }
            int quantity = Integer.parseInt(parts[5]);
            if (quantity < 1 || quantity > 100) {
                return Optional.empty();
            }
            return Optional.of(new TicketPayload(parts[1], ticketId, parts[3], parts[4], quantity, parts[6]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
