package io.github.diegofranciscog.textrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import io.github.diegofranciscog.textrack.domain.Cut;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.TicketPdfService.TicketPdf;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/** El PDF impreso debe contener QR legibles cuya firma verifica el servidor. */
class TicketPdfServiceTest {

    private static final float DPI = 200;

    private final TicketSigner signer = new TicketSigner(secret(), "k1");
    private final CutRepository cuts = mock(CutRepository.class);
    private final TicketPdfService service = new TicketPdfService(cuts);

    private static String secret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private TicketInfo ticket(int number, String operation) {
        TicketPayload payload = signer.sign(UUID.randomUUID(), "CT-00007-%03d".formatted(number), operation, 20);
        return new TicketInfo(payload.ticketId(), number, payload.bundleCode(), "M", "Azul marino", 1, 10, operation,
                "Operación con tildes áéíóú ñ", 20, payload.keyId(), payload.signature(), "CT-00007", "ORD-2026-0007",
                "POLO-PIQUE-01");
    }

    @Test
    void printsTwelveScannableSignedTicketsPerA4Page() throws Exception {
        List<TicketInfo> tickets = IntStream.rangeClosed(1, 14).mapToObj(i -> ticket(i, "OP" + (i * 10))).toList();
        when(cuts.findById(7L)).thenReturn(Optional.of(
                new Cut(7, "CT-00007", 1, "ORD-2026-0007", "Azul marino", 20, OffsetDateTime.now(), 14, 280)));
        when(cuts.findTicketsByCut(7L)).thenReturn(tickets);

        TicketPdf pdf = service.render(7);

        assertThat(pdf.fileName()).isEqualTo("tickets-CT-00007.pdf");
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, DPI);
            Set<String> decoded = new HashSet<>();
            for (int cell = 0; cell < 12; cell++) {
                decoded.add(decodeCell(page, cell % 2, cell / 2));
            }

            assertThat(decoded).hasSize(12);
            assertThat(decoded).allSatisfy(text -> {
                TicketPayload payload = TicketPayload.parse(text).orElseThrow();
                assertThat(signer.verify(payload)).isTrue();
            });
            Set<String> expected = tickets.subList(0, 12).stream().map(t -> new TicketPayload(t.keyId(), t.id(),
                    t.bundleCode(), t.operationCode(), t.quantity(), t.signature()).encode()).collect(Collectors.toSet());
            assertThat(decoded).isEqualTo(expected);
        }
    }


    /** Recorta la mitad izquierda de la etiqueta (donde va el QR), como la vería un escáner. */
    private static String decodeCell(BufferedImage page, int column, int row) throws Exception {
        float scale = DPI / 72f;
        float cellWidth = (595.28f - 40) / 2;
        float cellHeight = (841.89f - 40) / 6;
        int x = Math.round((20 + column * cellWidth) * scale);
        int y = Math.round((20 + row * cellHeight) * scale);
        BufferedImage crop = page.getSubimage(x, y, Math.round(cellWidth * 0.45f * scale), Math.round(cellHeight * scale));
        int[] pixels = crop.getRGB(0, 0, crop.getWidth(), crop.getHeight(), null, 0, crop.getWidth());
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(crop.getWidth(), crop.getHeight(), pixels)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
    }

    @Test
    void keepsOnlyPrintableLatin1Characters() {
        assertThat(TicketPdfService.latin1("Pegar ñandú ✓ 🚀")).isEqualTo("Pegar ñandú  ");
    }
}
