package app.bringme.api.web;

import app.bringme.api.config.BringmeProperties;
import app.bringme.api.pricing.PricingPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Grille tarifaire publiée au front : la seule source des tranches, libellés et suppléments. */
@RestController
public class PricingController {

    public record Grid(int baseCents, int includedKm, int perKmCents, BigDecimal commissionRate, List<BringmeProperties.WeightTier> weightTiers) {}

    private final PricingPolicy pricing;

    public PricingController(PricingPolicy pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/api/pricing")
    public Grid grid() {
        BringmeProperties.Pricing g = pricing.grid();
        return new Grid(g.baseCents(), g.includedKm(), g.perKmCents(), g.commissionRate(), g.weightTiers());
    }
}
