package app.bringme.api.web;

import app.bringme.api.config.BringmeProperties;
import app.bringme.api.pricing.PricingPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Grille tarifaire publiée au front : la seule source des gabarits, libellés et tarifs. */
@RestController
public class PricingController {

    public record Grid(int includedKm, BigDecimal commissionRate, List<BringmeProperties.SizeTier> sizeTiers) {}

    private final PricingPolicy pricing;

    public PricingController(PricingPolicy pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/api/pricing")
    public Grid grid() {
        BringmeProperties.Pricing g = pricing.grid();
        return new Grid(g.includedKm(), g.commissionRate(), g.sizeTiers());
    }
}
