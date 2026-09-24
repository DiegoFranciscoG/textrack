package io.github.diegofranciscog.textrack.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.diegofranciscog.textrack.domain.Cut;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDF A4 imprimible con los tickets de un corte: 2 × 6 etiquetas por página, cada una con su QR firmado
 * (dibujado como vectores, nítido a cualquier escala) y los datos legibles del bulto y la operación.
 */
@Service
public class TicketPdfService {

    private static final float MARGIN = 20f;
    private static final int COLUMNS = 2;
    private static final int ROWS = 6;
    private static final float QR_SIZE = 104f;
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final CutRepository cuts;

    public TicketPdfService(CutRepository cuts) {
        this.cuts = cuts;
    }

    @Transactional(readOnly = true)
    public TicketPdf render(long cutId) {
        Cut cut = cuts.findById(cutId).orElseThrow(() -> new NotFoundException("Corte no encontrado"));
        List<TicketInfo> tickets = cuts.findTicketsByCut(cutId);
        return new TicketPdf("tickets-" + cut.code() + ".pdf", render(cut, tickets));
    }

    private byte[] render(Cut cut, List<TicketInfo> tickets) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Tickets " + cut.code() + " - " + cut.orderCode());
            info.setCreator("textrack");
            document.setDocumentInformation(info);

            PDRectangle a4 = PDRectangle.A4;
            float cellWidth = (a4.getWidth() - 2 * MARGIN) / COLUMNS;
            float cellHeight = (a4.getHeight() - 2 * MARGIN) / ROWS;
            int perPage = COLUMNS * ROWS;
            for (int start = 0; start < Math.max(tickets.size(), 1); start += perPage) {
                PDPage page = new PDPage(a4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    List<TicketInfo> pageTickets = tickets.subList(start, Math.min(start + perPage, tickets.size()));
                    for (int i = 0; i < pageTickets.size(); i++) {
                        float x = MARGIN + (i % COLUMNS) * cellWidth;
                        float y = a4.getHeight() - MARGIN - (i / COLUMNS + 1) * cellHeight;
                        drawTicket(content, pageTickets.get(i), x, y, cellWidth, cellHeight);
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de tickets", e);
        }
    }

    private void drawTicket(PDPageContentStream content, TicketInfo ticket, float x, float y, float width, float height)
            throws IOException {
        content.setStrokingColor(new Color(150, 150, 150));
        content.setLineDashPattern(new float[] {3, 3}, 0);
        content.addRect(x + 2, y + 2, width - 4, height - 4);
        content.stroke();
        content.setLineDashPattern(new float[] {}, 0);

        String payload = new TicketPayload(ticket.keyId(), ticket.id(), ticket.bundleCode(), ticket.operationCode(),
                ticket.quantity(), ticket.signature()).encode();
        float qrX = x + 10;
        float qrY = y + (height - QR_SIZE) / 2;
        drawQr(content, payload, qrX, qrY);

        float textX = qrX + QR_SIZE + 10;
        float maxWidth = x + width - textX - 8;
        float line = y + height - 24;
        text(content, BOLD, 13, textX, line, ticket.bundleCode(), maxWidth);
        text(content, BOLD, 11, textX, line - 17, ticket.operationCode() + " · " + ticket.operationName(), maxWidth);
        text(content, REGULAR, 10, textX, line - 33, "Talla " + ticket.sizeCode() + " · " + ticket.color(), maxWidth);
        text(content, REGULAR, 10, textX, line - 47, "Cantidad: " + ticket.quantity() + " piezas", maxWidth);
        text(content, REGULAR, 9, textX, line - 61, ticket.orderCode() + " · " + ticket.styleCode(), maxWidth);
        text(content, REGULAR, 7, textX, y + 12, "Ticket firmado (" + ticket.keyId() + ") "
                + ticket.id().toString().substring(0, 8), maxWidth);
    }

    private static void drawQr(PDPageContentStream content, String payload, float x, float y) throws IOException {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo codificar el QR", e);
        }
        float module = QR_SIZE / matrix.getWidth();
        content.setNonStrokingColor(Color.BLACK);
        for (int row = 0; row < matrix.getHeight(); row++) {
            int col = 0;
            while (col < matrix.getWidth()) {
                if (!matrix.get(col, row)) {
                    col++;
                    continue;
                }
                int runStart = col;
                while (col < matrix.getWidth() && matrix.get(col, row)) {
                    col++;
                }
                content.addRect(x + runStart * module, y + QR_SIZE - (row + 1) * module,
                        (col - runStart) * module, module);
            }
        }
        content.fill();
    }

    private static void text(PDPageContentStream content, PDType1Font font, float size, float x, float y, String value,
                             float maxWidth) throws IOException {
        String safe = latin1(value);
        while (!safe.isEmpty() && font.getStringWidth(safe) / 1000 * size > maxWidth) {
            safe = safe.substring(0, safe.length() - 1);
        }
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe);
        content.endText();
    }

    /** Las fuentes estándar de PDF usan WinAnsi: se descartan caracteres fuera de Latin-1 imprimible. */
    static String latin1(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if ((c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public record TicketPdf(String fileName, byte[] content) {
    }
}
