package io.github.diegofranciscog.textrack.domain;

import java.time.OffsetDateTime;

public record Cut(long id, String code, long productionOrderId, String orderCode, String color, int maxBundleSize,
                  OffsetDateTime createdAt, int bundles, int pieces) {
}
