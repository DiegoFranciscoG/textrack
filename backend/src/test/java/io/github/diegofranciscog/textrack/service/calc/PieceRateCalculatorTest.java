package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.DailyInput;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.DailyPay;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.PieceWork;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.Premium;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.RestPay;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.Shift;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Destajo diario (Código del Trabajo, Arts. 16, 49, 53, 55, 81, 82)")
class PieceRateCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final BigDecimal SBU_2026 = new BigDecimal("482.00");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 23);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 26);
    private static final BigDecimal RATE = new BigDecimal("0.0400");
    private static final BigDecimal SAM = new BigDecimal("1.0000");

    private static Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(ZONE).toInstant();
    }

    private static PieceWork piece(Instant when, int quantity, BigDecimal rate) {
        return new PieceWork(when, "OP10", quantity, rate, SAM);
    }

    private static Shift dayShift(LocalDate date) {
        return new Shift(at(date, 7, 0), at(date, 15, 30), 30);
    }

    private static DailyPay pay(LocalDate date, Shift shift, List<PieceWork> pieces) {
        Instant endOfDay = at(date, 23, 59);
        return PieceRateCalculator.daily(new DailyInput(date, ZONE, SBU_2026, shift, pieces, endOfDay));
    }

    @Test
    @DisplayName("Piso por hora = SBU/240 y piso de jornada completa = SBU/30")
    void hourlyFloorIsSbuOver240() {
        assertThat(PieceRateCalculator.hourlyFloor(SBU_2026)).isEqualByComparingTo("2.008333");
        DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY), List.of());
        assertThat(day.floor()).isEqualByComparingTo("16.07");
        assertThat(day.ordinaryHours()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("Pago ordinario sobre el piso: sin complemento ni alerta")
    void ordinaryPayAboveFloor() {
        DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY), List.of(piece(at(WEDNESDAY, 10, 0), 500, RATE)));

        assertThat(day.ordinaryPay()).isEqualByComparingTo("20.00");
        assertThat(day.belowFloor()).isFalse();
        assertThat(day.topUp()).isEqualByComparingTo("0.00");
        assertThat(day.totalPay()).isEqualByComparingTo("20.00");
        assertThat(day.pieces()).isEqualTo(500);
    }

    @Test
    @DisplayName("Pago ordinario bajo el SBU prorrateado: alerta y complemento hasta el piso")
    void payBelowFloorRaisesAlertAndTopUp() {
        DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY), List.of(piece(at(WEDNESDAY, 10, 0), 300, RATE)));

        assertThat(day.ordinaryPay()).isEqualByComparingTo("12.00");
        assertThat(day.belowFloor()).isTrue();
        assertThat(day.topUp()).isEqualByComparingTo("4.07");
        assertThat(day.totalPay()).isEqualByComparingTo("16.07");
    }

    @Test
    @DisplayName("Pago exactamente igual al piso no genera alerta")
    void payEqualToFloorIsNotBelow() {
        // 16,07 USD = 1607 piezas a 0,01
        DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY),
                List.of(piece(at(WEDNESDAY, 9, 0), 1607, new BigDecimal("0.0100"))));

        assertThat(day.belowFloor()).isFalse();
        assertThat(day.topUp()).isEqualByComparingTo("0");
    }

    @Nested
    @DisplayName("Recargos")
    class Premiums {

        @Test
        @DisplayName("Art. 55 num. 3: piezas después de 8 h de jornada +50 %")
        void supplementaryPiecesGetFiftyPercent() {
            BigDecimal rate = new BigDecimal("0.0500");
            DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY), List.of(
                    piece(at(WEDNESDAY, 10, 0), 400, rate),
                    piece(at(WEDNESDAY, 16, 0), 100, rate)));

            assertThat(day.ordinaryPay()).isEqualByComparingTo("20.00");
            assertThat(day.extraPay()).isEqualByComparingTo("5.00");
            assertThat(day.premiumPay()).isEqualByComparingTo("2.50");
            assertThat(day.totalPay()).isEqualByComparingTo("27.50");
            assertThat(day.lines()).extracting(PieceRateCalculator.PayLine::premium)
                    .containsExactly(Premium.NONE, Premium.SUPPLEMENTARY);
        }

        @Test
        @DisplayName("Arts. 49 y 55: nocturno ordinario +25 %, suplementario antes de 24h00 +50 %, después +100 %")
        void nightAndSupplementaryNightPremiums() {
            Shift afternoon = new Shift(at(WEDNESDAY, 15, 0), at(WEDNESDAY.plusDays(1), 2, 0), 30);
            BigDecimal rate = new BigDecimal("0.1000");
            DailyPay day = pay(WEDNESDAY, afternoon, List.of(
                    piece(at(WEDNESDAY, 16, 0), 10, rate),              // ordinario diurno
                    piece(at(WEDNESDAY, 20, 0), 10, rate),              // ordinario nocturno
                    piece(at(WEDNESDAY, 23, 45), 10, rate),             // suplementario antes de 24h00
                    piece(at(WEDNESDAY.plusDays(1), 1, 0), 10, rate)));  // suplementario de madrugada

            assertThat(day.lines()).extracting(PieceRateCalculator.PayLine::premium).containsExactly(
                    Premium.NONE, Premium.NIGHT, Premium.SUPPLEMENTARY, Premium.SUPPLEMENTARY_NIGHT);
            assertThat(day.lines()).extracting(PieceRateCalculator.PayLine::premiumAmount).containsExactly(
                    new BigDecimal("0.00"), new BigDecimal("0.25"), new BigDecimal("0.50"), new BigDecimal("1.00"));
            assertThat(day.ordinaryPay()).isEqualByComparingTo("2.00");
            assertThat(day.extraPay()).isEqualByComparingTo("2.00");
            assertThat(day.premiumPay()).isEqualByComparingTo("1.75");
        }

        @Test
        @DisplayName("Art. 55 num. 4: sábado o domingo +100 % y sin piso obligatorio")
        void weekendDoublesPay() {
            DailyPay day = pay(SATURDAY, dayShift(SATURDAY), List.of(piece(at(SATURDAY, 9, 0), 100, RATE)));

            assertThat(day.weekend()).isTrue();
            assertThat(day.floor()).isEqualByComparingTo("0");
            assertThat(day.belowFloor()).isFalse();
            assertThat(day.premiumPay()).isEqualByComparingTo("4.00");
            assertThat(day.totalPay()).isEqualByComparingTo("8.00");
        }
    }

    @Test
    @DisplayName("Jornada abierta: el piso se prorratea con las horas trabajadas hasta ahora (Art. 82)")
    void openShiftProratesFloor() {
        Shift open = new Shift(at(WEDNESDAY, 7, 0), null, 30);
        Instant elevenAm = at(WEDNESDAY, 11, 0);
        DailyPay day = PieceRateCalculator.daily(new DailyInput(WEDNESDAY, ZONE, SBU_2026, open,
                List.of(piece(at(WEDNESDAY, 10, 30), 150, RATE)), elevenAm));

        assertThat(day.attendedMinutes()).isEqualByComparingTo("210");
        assertThat(day.ordinaryHours()).isEqualByComparingTo("3.50");
        assertThat(day.floor()).isEqualByComparingTo("7.03");
        assertThat(day.belowFloor()).isTrue();
        assertThat(day.topUp()).isEqualByComparingTo("1.03");
    }

    @Test
    @DisplayName("Sin asistencia registrada se asume jornada completa y no se calcula eficiencia")
    void withoutAttendanceAssumesFullDay() {
        DailyPay day = pay(WEDNESDAY, null, List.of(piece(at(WEDNESDAY, 10, 0), 500, RATE)));

        assertThat(day.attendanceRecorded()).isFalse();
        assertThat(day.floor()).isEqualByComparingTo("16.07");
        assertThat(day.efficiency()).isNull();
    }

    @Test
    @DisplayName("Eficiencia OIT = minutos estándar producidos / minutos de asistencia")
    void efficiencyUsesEarnedMinutes() {
        DailyPay day = pay(WEDNESDAY, dayShift(WEDNESDAY), List.of(piece(at(WEDNESDAY, 10, 0), 400, RATE)));

        assertThat(day.earnedMinutes()).isEqualByComparingTo("400.00");
        assertThat(day.attendedMinutes()).isEqualByComparingTo("480");
        assertThat(day.efficiency()).isEqualByComparingTo("0.8333");
    }

    @Nested
    @DisplayName("Descanso semanal (Art. 53)")
    class WeeklyRest {

        private DailyPay dayWithTotal(LocalDate date, String total) {
            return new DailyPay(date, 0, BigDecimal.ZERO, new BigDecimal(total), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(total), false, false, BigDecimal.ZERO,
                    null, null, true, List.of());
        }

        @Test
        @DisplayName("Se paga con el promedio de lunes a viernes")
        void usesWeekdayAverage() {
            List<DailyPay> week = Collections.nCopies(5, dayWithTotal(WEDNESDAY, "20.00"));

            RestPay rest = PieceRateCalculator.weeklyRest(week, SBU_2026);

            assertThat(rest.weekdayAverage()).isEqualByComparingTo("20.00");
            assertThat(rest.amount()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("Nunca es inferior al SBU diario")
        void neverBelowMinimum() {
            List<DailyPay> week = Collections.nCopies(5, dayWithTotal(WEDNESDAY, "10.00"));

            RestPay rest = PieceRateCalculator.weeklyRest(week, SBU_2026);

            assertThat(rest.minimumDaily()).isEqualByComparingTo("16.07");
            assertThat(rest.amount()).isEqualByComparingTo("32.14");
        }
    }
}
