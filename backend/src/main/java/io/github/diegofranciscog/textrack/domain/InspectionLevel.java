package io.github.diegofranciscog.textrack.domain;

/** Niveles de inspección de la Tabla I (ISO 2859-1 / MIL-STD-105E). Cada cadena da la letra por rango de lote. */
public enum InspectionLevel {
    S1("AAAABBBBCCCCDDD"),
    S2("AAABBBCCCDDDEEE"),
    S3("AABBCCDDEEFFGGH"),
    S4("AABCCDEEFGGHJJK"),
    I("AABCCDEFGHJKLMN"),
    II("ABCDEFGHJKLMNPQ"),
    III("BCDEFGHJKLMNPQR");

    private final String letters;

    InspectionLevel(String letters) {
        this.letters = letters;
    }

    public String letters() {
        return letters;
    }
}
