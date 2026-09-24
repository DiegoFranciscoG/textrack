package io.github.diegofranciscog.textrack.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FabricRoll(long id, String code, String supplier, String dyeLot, String color, BigDecimal lengthM,
                         BigDecimal widthCm, BigDecimal remainingM, RollStatus status, OffsetDateTime receivedAt,
                         Integer totalPoints, BigDecimal pointsPer100SqYd) {
}
