package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.ScanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReadingDtos {

    public static final int MAX_BATCH = 500;

    private ReadingDtos() {
    }

    /**
     * Lectura de un ticket. {@code clientReadingId} lo genera el dispositivo al escanear y actúa como clave de
     * idempotencia: reenviarlo nunca crea otra lectura.
     */
    public record ScanRequest(
            @NotNull UUID clientReadingId,
            @NotBlank @Size(max = 300) String payload,
            @NotBlank @Size(max = 20) String operatorCode,
            @NotNull OffsetDateTime scannedAt,
            @Size(max = 64) String deviceId) {
    }

    public record BatchScanRequest(@NotEmpty @Size(max = MAX_BATCH) List<@Valid ScanRequest> readings) {
    }

    public record ScanResult(UUID clientReadingId, ScanStatus status, String message, Long readingId, UUID ticketId,
                             String bundleCode, String operationCode, Integer quantity, String registeredBy,
                             OffsetDateTime registeredAt) {
    }

    public record BatchScanResponse(List<ScanResult> results, int accepted, int duplicates, int rejected) {
    }

    public record RecentReadingResponse(long id, OffsetDateTime scannedAt, String operatorCode, String operatorName,
                                        String lineCode, String bundleCode, String operationCode, int quantity,
                                        String source) {
    }
}
