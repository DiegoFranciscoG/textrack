package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.service.ProductionOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogs")
@Tag(name = "Catálogos")
public class CatalogController {

    private final ProductionOrderService orders;

    public CatalogController(ProductionOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/sizes")
    public List<String> sizes() {
        return orders.sizes();
    }
}
