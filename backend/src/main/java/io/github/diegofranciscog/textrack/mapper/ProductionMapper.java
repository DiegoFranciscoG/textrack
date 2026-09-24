package io.github.diegofranciscog.textrack.mapper;

import io.github.diegofranciscog.textrack.domain.Bundle;
import io.github.diegofranciscog.textrack.domain.Cut;
import io.github.diegofranciscog.textrack.domain.FabricRoll;
import io.github.diegofranciscog.textrack.domain.OrderLine;
import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.BundleResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutSummary;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderLineResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderSummary;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollUsageResponse;
import io.github.diegofranciscog.textrack.repository.CutRepository.RollUsage;
import java.util.List;

public final class ProductionMapper {

    private ProductionMapper() {
    }

    public static OrderSummary toSummary(ProductionOrder order) {
        return new OrderSummary(order.id(), order.code(), order.styleCode(), order.styleName(), order.customer(),
                order.dueDate(), order.status(), order.createdAt());
    }

    public static OrderResponse toResponse(ProductionOrder order, List<OrderLine> lines, List<Cut> cuts,
                                           int finishedQuantity) {
        int total = lines.stream().mapToInt(OrderLine::quantity).sum();
        int cut = lines.stream().mapToInt(OrderLine::cutQuantity).sum();
        return new OrderResponse(order.id(), order.code(), order.styleId(), order.styleCode(), order.styleName(),
                order.customer(), order.dueDate(), order.status(), order.createdAt(), total, cut, finishedQuantity,
                lines.stream().map(line -> new OrderLineResponse(line.sizeCode(), line.color(), line.quantity(),
                        line.cutQuantity())).toList(),
                cuts.stream().map(ProductionMapper::toSummary).toList());
    }

    public static CutSummary toSummary(Cut cut) {
        return new CutSummary(cut.id(), cut.code(), cut.color(), cut.bundles(), cut.pieces(), cut.createdAt());
    }

    public static BundleResponse toResponse(Bundle bundle) {
        return new BundleResponse(bundle.id(), bundle.code(), bundle.bundleNumber(), bundle.sizeCode(), bundle.color(),
                bundle.quantity(), bundle.rollCode(), bundle.dyeLot());
    }

    public static CutResponse toResponse(Cut cut, List<RollUsage> rolls, List<Bundle> bundles, int tickets) {
        return new CutResponse(cut.id(), cut.code(), cut.orderCode(), cut.color(), cut.maxBundleSize(),
                cut.createdAt(), cut.pieces(), tickets,
                rolls.stream().map(roll -> new RollUsageResponse(roll.rollCode(), roll.dyeLot(), roll.plies(),
                        roll.metersUsed())).toList(),
                bundles.stream().map(ProductionMapper::toResponse).toList());
    }

    public static RollResponse toResponse(FabricRoll roll) {
        return new RollResponse(roll.id(), roll.code(), roll.supplier(), roll.dyeLot(), roll.color(), roll.lengthM(),
                roll.widthCm(), roll.remainingM(), roll.status(), roll.receivedAt(), roll.totalPoints(),
                roll.pointsPer100SqYd());
    }
}
