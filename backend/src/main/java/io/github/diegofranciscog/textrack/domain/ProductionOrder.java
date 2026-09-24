package io.github.diegofranciscog.textrack.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ProductionOrder(long id, String code, long styleId, String styleCode, String styleName, String customer,
                              LocalDate dueDate, OrderStatus status, OffsetDateTime createdAt) {
}
