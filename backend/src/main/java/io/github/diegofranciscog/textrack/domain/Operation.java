package io.github.diegofranciscog.textrack.domain;

import java.math.BigDecimal;

/** Operación de la ruta de un estilo con su SAM y la tarifa vigente (puede ser null si no tiene tarifa). */
public record Operation(long id, long styleId, int sequence, String code, String name, MachineType machineType,
                        BigDecimal samMinutes, BigDecimal currentRateUsd) {
}
