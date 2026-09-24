package io.github.diegofranciscog.textrack.mapper;

import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.OperatorPay;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.PayLineResponse;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.RestPayResponse;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.DailyPay;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.PayLine;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.RestPay;
import java.time.ZoneOffset;

public final class PayrollMapper {

    private PayrollMapper() {
    }

    public static OperatorPay toResponse(Operator operator, DailyPay pay) {
        return new OperatorPay(operator.id(), operator.code(), operator.fullName(), operator.lineCode(), pay.workDate(),
                pay.pieces(), pay.earnedMinutes(), pay.attendedMinutes(), pay.efficiency(), pay.ordinaryPay(),
                pay.extraPay(), pay.premiumPay(), pay.floor(), pay.topUp(), pay.totalPay(), pay.belowFloor(),
                pay.weekend(), pay.attendanceRecorded());
    }

    public static PayLineResponse toResponse(PayLine line) {
        return new PayLineResponse(line.scannedAt().atOffset(ZoneOffset.UTC), line.operationCode(), line.quantity(),
                line.rateUsd(), line.baseAmount(), line.premium().name(), line.premium().percent(),
                line.premiumAmount());
    }

    public static RestPayResponse toResponse(RestPay rest) {
        return new RestPayResponse(rest.weekdayAverage(), rest.minimumDaily(), rest.dailyRate(), rest.amount());
    }
}
