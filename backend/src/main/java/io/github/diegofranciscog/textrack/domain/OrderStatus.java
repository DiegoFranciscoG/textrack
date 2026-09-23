package io.github.diegofranciscog.textrack.domain;

public enum OrderStatus {
    PLANNED, CUTTING, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean acceptsCuts() {
        return this == PLANNED || this == CUTTING || this == IN_PROGRESS;
    }
}
