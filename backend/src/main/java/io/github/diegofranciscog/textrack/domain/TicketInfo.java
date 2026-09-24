package io.github.diegofranciscog.textrack.domain;

import java.util.UUID;

/** Ticket con los datos necesarios para verificarlo e imprimirlo. */
public record TicketInfo(UUID id, long bundleId, String bundleCode, String sizeCode, String color, long operationId,
                         int operationSequence, String operationCode, String operationName, int quantity,
                         String keyId, String signature, String cutCode, String orderCode, String styleCode) {
}
