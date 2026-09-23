package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TicketSignerTest {

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private final TicketSigner signer = new TicketSigner(randomSecret(), "k1");
    private final UUID ticketId = UUID.randomUUID();

    @Test
    void signedPayloadRoundTripsThroughQrText() {
        TicketPayload signed = signer.sign(ticketId, "CT-00001-003", "OP20", 20);

        TicketPayload parsed = TicketPayload.parse(signed.encode()).orElseThrow();

        assertThat(parsed).isEqualTo(signed);
        assertThat(parsed.signature()).hasSize(22);
        assertThat(signer.verify(parsed)).isTrue();
    }

    @Test
    void tamperedQuantityOrBundleFailsVerification() {
        TicketPayload signed = signer.sign(ticketId, "CT-00001-003", "OP20", 20);

        TicketPayload moreQuantity = TicketPayload.parse(signed.encode().replace(".20.", ".40.")).orElseThrow();
        TicketPayload otherBundle = TicketPayload.parse(signed.encode().replace("CT-00001-003", "CT-00001-004"))
                .orElseThrow();

        assertThat(signer.verify(moreQuantity)).isFalse();
        assertThat(signer.verify(otherBundle)).isFalse();
    }

    @Test
    void signatureFromAnotherKeyIsRejected() {
        TicketSigner attacker = new TicketSigner(randomSecret(), "k1");
        TicketPayload forged = attacker.sign(ticketId, "CT-00001-003", "OP20", 20);

        assertThat(signer.verify(forged)).isFalse();
    }

    @Test
    void unknownKeyIdIsRejected() {
        TicketSigner otherKeyId = new TicketSigner(randomSecret(), "k2");

        assertThat(signer.verify(otherKeyId.sign(ticketId, "CT-00001-003", "OP20", 20))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hola", "TT1.k1.no-uuid.B.OP.1.AAAAAAAAAAAAAAAAAAAAAA",
        "TT2.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-1.OP10.5.AAAAAAAAAAAAAAAAAAAAAA",
        "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-1.OP10.0.AAAAAAAAAAAAAAAAAAAAAA",
        "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-1.OP10.500.AAAAAAAAAAAAAAAAAAAAAA",
        "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.ct-1.OP10.5.AAAAAAAAAAAAAAAAAAAAAA",
        "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-1.OP10.5.corta",
        "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-1.OP10.5.AAAAAAAAAAAAAAAAAAAAAA.extra"})
    void malformedPayloadsAreNotParsed(String raw) {
        assertThat(TicketPayload.parse(raw)).isEmpty();
    }

    @Test
    void nullAndOversizedPayloadsAreNotParsed() {
        assertThat(TicketPayload.parse(null)).isEmpty();
        assertThat(TicketPayload.parse("TT1." + "A".repeat(400))).isEmpty();
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new TicketSigner("corta", "k1")).isInstanceOf(IllegalArgumentException.class);
    }
}
