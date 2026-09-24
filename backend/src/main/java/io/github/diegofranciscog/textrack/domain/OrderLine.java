package io.github.diegofranciscog.textrack.domain;

/** Línea talla × color de una orden, con lo ya cortado. */
public record OrderLine(long id, String sizeCode, String color, int quantity, int cutQuantity) {
}
