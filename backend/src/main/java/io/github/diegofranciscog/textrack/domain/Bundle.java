package io.github.diegofranciscog.textrack.domain;

public record Bundle(long id, String code, long cutId, long rollId, String rollCode, String dyeLot, int bundleNumber,
                     String sizeCode, String color, int quantity) {
}
