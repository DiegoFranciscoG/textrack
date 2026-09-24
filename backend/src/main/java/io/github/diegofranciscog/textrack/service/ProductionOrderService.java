package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateOrderRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderLineRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderSummary;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.ProductionMapper;
import io.github.diegofranciscog.textrack.repository.CatalogRepository;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.EngineeringRepository;
import io.github.diegofranciscog.textrack.repository.ProductionOrderRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionOrderService {

    private final ProductionOrderRepository orders;
    private final EngineeringRepository engineering;
    private final CatalogRepository catalogs;
    private final CutRepository cuts;
    private final PlantTime time;

    public ProductionOrderService(ProductionOrderRepository orders, EngineeringRepository engineering,
                                  CatalogRepository catalogs, CutRepository cuts, PlantTime time) {
        this.orders = orders;
        this.engineering = engineering;
        this.catalogs = catalogs;
        this.cuts = cuts;
        this.time = time;
    }

    public List<String> sizes() {
        return catalogs.sizes();
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> findAll() {
        return orders.findAll().stream().map(ProductionMapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse find(long id) {
        ProductionOrder order = orders.findById(id).orElseThrow(() -> new NotFoundException("Orden no encontrada"));
        return ProductionMapper.toResponse(order, orders.findLines(id), cuts.findByOrder(id), orders.finishedPieces(id));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long userId) {
        engineering.findStyle(request.styleId()).orElseThrow(() -> new NotFoundException("Estilo no encontrado"));
        if (engineering.findOperations(request.styleId(), time.today()).isEmpty()) {
            throw new BusinessRuleException("El estilo no tiene operaciones: define su ruta antes de crear órdenes");
        }
        Set<String> validSizes = new HashSet<>(catalogs.sizes());
        Set<String> seen = new HashSet<>();
        for (OrderLineRequest line : request.lines()) {
            if (!validSizes.contains(line.sizeCode())) {
                throw new BusinessRuleException("Talla no válida: " + line.sizeCode());
            }
            if (!seen.add(line.sizeCode() + "|" + line.color().trim().toLowerCase())) {
                throw new BusinessRuleException("Talla y color repetidos: " + line.sizeCode() + " " + line.color());
            }
        }
        String code = orders.nextCode(time.today().getYear());
        long id = orders.insert(code, request.styleId(), request.customer().trim(), request.dueDate(), userId);
        request.lines().forEach(line -> orders.insertLine(id, line.sizeCode(), line.color().trim(), line.quantity()));
        return find(id);
    }
}
