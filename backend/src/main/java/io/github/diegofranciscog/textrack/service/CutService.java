package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.domain.Cut;
import io.github.diegofranciscog.textrack.domain.FabricRoll;
import io.github.diegofranciscog.textrack.domain.Operation;
import io.github.diegofranciscog.textrack.domain.OrderLine;
import io.github.diegofranciscog.textrack.domain.OrderStatus;
import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import io.github.diegofranciscog.textrack.domain.RollStatus;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateCutRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutSummary;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollLayRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.SizeRatioRequest;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.ProductionMapper;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.CutRepository.NewTicket;
import io.github.diegofranciscog.textrack.repository.EngineeringRepository;
import io.github.diegofranciscog.textrack.repository.FabricRollRepository;
import io.github.diegofranciscog.textrack.repository.ProductionOrderRepository;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.BundlePlan;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.RollLay;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.SizeRatio;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corte: valida el tendido (rollos aprobados, mismo color, metros disponibles) y las cantidades contra la orden,
 * genera los bultos y un ticket firmado por cada bulto × operación de la ruta, todo en una transacción.
 */
@Service
public class CutService {

    private final CutRepository cuts;
    private final ProductionOrderRepository orders;
    private final FabricRollRepository rolls;
    private final EngineeringRepository engineering;
    private final TicketSigner signer;
    private final PlantTime time;
    private final BigDecimal overcutTolerance;

    public CutService(CutRepository cuts, ProductionOrderRepository orders, FabricRollRepository rolls,
                      EngineeringRepository engineering, TicketSigner signer, PlantTime time,
                      AppProperties properties) {
        this.cuts = cuts;
        this.orders = orders;
        this.rolls = rolls;
        this.engineering = engineering;
        this.signer = signer;
        this.time = time;
        this.overcutTolerance = properties.production().overcutTolerance();
    }

    @Transactional(readOnly = true)
    public List<CutSummary> findAll() {
        return cuts.findAll().stream().map(ProductionMapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CutResponse find(long cutId) {
        Cut cut = cuts.findById(cutId).orElseThrow(() -> new NotFoundException("Corte no encontrado"));
        return ProductionMapper.toResponse(cut, cuts.findRollUsages(cutId), cuts.findBundles(cutId),
                cuts.findTicketsByCut(cutId).size());
    }

    @Transactional
    public CutResponse create(long orderId, CreateCutRequest request, Long userId) {
        ProductionOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Orden no encontrada"));
        if (!order.status().acceptsCuts()) {
            throw new BusinessRuleException("La orden está " + order.status() + " y no admite cortes");
        }
        String color = request.color().trim();
        Map<String, OrderLine> linesBySize = new HashMap<>();
        orders.findLines(orderId).stream()
                .filter(line -> line.color().equalsIgnoreCase(color))
                .forEach(line -> linesBySize.put(line.sizeCode(), line));
        if (linesBySize.isEmpty()) {
            throw new BusinessRuleException("La orden no tiene el color " + color);
        }
        List<SizeRatio> ratios = validateRatios(request.sizeRatios(), linesBySize);
        List<RollLay> lays = validateRolls(request.rolls(), color);
        validateQuantities(lays, ratios, linesBySize);

        List<Operation> route = engineering.findOperations(order.styleId(), time.today());
        if (route.isEmpty()) {
            throw new BusinessRuleException("El estilo no tiene operaciones definidas");
        }

        String code = cuts.nextCode();
        String canonicalColor = linesBySize.values().iterator().next().color();
        long cutId = cuts.insert(code, orderId, canonicalColor, request.maxBundleSize(), userId);
        ratios.forEach(ratio -> cuts.insertRatio(cutId, ratio.sizeCode(), ratio.piecesPerPly()));
        for (RollLayRequest roll : request.rolls()) {
            cuts.insertRollUsage(cutId, roll.rollId(), roll.plies(), roll.metersUsed());
            rolls.consume(roll.rollId(), roll.metersUsed());
        }

        List<NewTicket> tickets = new ArrayList<>();
        for (BundlePlan plan : BundleGenerator.generate(lays, ratios, request.maxBundleSize())) {
            String bundleCode = "%s-%03d".formatted(code, plan.bundleNumber());
            long bundleId = cuts.insertBundle(bundleCode, cutId, plan.rollId(), plan.bundleNumber(), plan.sizeCode(),
                    canonicalColor, plan.quantity());
            for (Operation operation : route) {
                TicketPayload payload = signer.sign(UUID.randomUUID(), bundleCode, operation.code(), plan.quantity());
                tickets.add(new NewTicket(payload.ticketId(), bundleId, operation.id(), plan.quantity(),
                        payload.keyId(), payload.signature()));
            }
        }
        cuts.insertTickets(tickets);
        if (order.status() == OrderStatus.PLANNED) {
            orders.updateStatus(orderId, OrderStatus.CUTTING);
        }
        return find(cutId);
    }

    private static List<SizeRatio> validateRatios(List<SizeRatioRequest> requested, Map<String, OrderLine> lines) {
        Set<String> seen = new HashSet<>();
        List<SizeRatio> ratios = new ArrayList<>();
        for (SizeRatioRequest ratio : requested) {
            if (!lines.containsKey(ratio.sizeCode())) {
                throw new BusinessRuleException("La talla " + ratio.sizeCode() + " no está en la orden para ese color");
            }
            if (!seen.add(ratio.sizeCode())) {
                throw new BusinessRuleException("Talla repetida en el trazo: " + ratio.sizeCode());
            }
            ratios.add(new SizeRatio(ratio.sizeCode(), ratio.piecesPerPly()));
        }
        return ratios;
    }

    private List<RollLay> validateRolls(List<RollLayRequest> requested, String color) {
        Set<Long> seen = new HashSet<>();
        List<RollLay> lays = new ArrayList<>();
        for (RollLayRequest lay : requested) {
            if (!seen.add(lay.rollId())) {
                throw new BusinessRuleException("Rollo repetido en el tendido");
            }
            FabricRoll roll = rolls.findByIdForUpdate(lay.rollId())
                    .orElseThrow(() -> new NotFoundException("Rollo no encontrado: " + lay.rollId()));
            if (roll.status() != RollStatus.APPROVED) {
                throw new BusinessRuleException("El rollo " + roll.code() + " no está aprobado por la inspección de 4 puntos"
                        + " (estado " + roll.status() + ")");
            }
            if (!roll.color().equalsIgnoreCase(color)) {
                throw new BusinessRuleException("El rollo " + roll.code() + " es de color " + roll.color());
            }
            if (lay.metersUsed().compareTo(roll.remainingM()) > 0) {
                throw new BusinessRuleException("El rollo " + roll.code() + " solo tiene " + roll.remainingM() + " m");
            }
            lays.add(new RollLay(roll.id(), lay.plies()));
        }
        return lays;
    }

    /** Lo cortado por talla no puede superar lo pedido pendiente más la tolerancia de sobrecorte (S9). */
    private void validateQuantities(List<RollLay> lays, List<SizeRatio> ratios, Map<String, OrderLine> lines) {
        int plies = lays.stream().mapToInt(RollLay::plies).sum();
        for (SizeRatio ratio : ratios) {
            OrderLine line = lines.get(ratio.sizeCode());
            int pieces = plies * ratio.piecesPerPly();
            int allowed = BigDecimal.valueOf(line.quantity())
                    .multiply(BigDecimal.ONE.add(overcutTolerance))
                    .setScale(0, RoundingMode.CEILING)
                    .intValue() - line.cutQuantity();
            if (pieces > allowed) {
                throw new BusinessRuleException("La talla %s excede lo pedido: se cortarían %d y quedan %d (con %s de tolerancia)"
                        .formatted(ratio.sizeCode(), pieces, Math.max(allowed, 0),
                                overcutTolerance.movePointRight(2).stripTrailingZeros().toPlainString() + " %"));
            }
        }
    }
}
