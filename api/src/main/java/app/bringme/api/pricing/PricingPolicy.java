package app.bringme.api.pricing;

import app.bringme.api.config.BringmeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grille du brief : base jusqu'à {@code includedKm}, puis un supplément par km commencé au-delà, plus un
 * supplément par kg commencé. Commission plateforme arrondie au centime le plus proche (half-up).
 * Calcul en entiers (centimes), aucune arithmétique flottante.
 */
@Component
public class PricingPolicy {

    private final BringmeProperties.Pricing grid;

    @Autowired
    public PricingPolicy(BringmeProperties props) {
        this(props.pricing());
    }

    public PricingPolicy(BringmeProperties.Pricing grid) {
        this.grid = Objects.requireNonNull(grid);
        if (grid.commissionRate().signum() < 0 || grid.commissionRate().compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Taux de commission hors de [0, 1[");
        }
    }

    public PriceBreakdown price(BigDecimal distanceKm, BigDecimal weightKg) {
        Objects.requireNonNull(distanceKm, "distanceKm");
        Objects.requireNonNull(weightKg, "weightKg");
        if (distanceKm.signum() < 0) {
            throw new IllegalArgumentException("Distance négative");
        }
        if (weightKg.signum() <= 0 || weightKg.compareTo(grid.maxWeightKg()) > 0) {
            throw new IllegalArgumentException("Poids hors limites");
        }
        BigDecimal distance = distanceKm.setScale(3, RoundingMode.HALF_UP);
        BigDecimal weight = weightKg.setScale(2, RoundingMode.HALF_UP);

        long extraKm = distance.subtract(BigDecimal.valueOf(grid.includedKm()))
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        long kg = weight.setScale(0, RoundingMode.CEILING).longValueExact();

        long price = grid.baseCents() + extraKm * grid.perKmCents() + kg * grid.perKgCents();
        int priceCents = Math.toIntExact(price);
        int commission = BigDecimal.valueOf(priceCents)
                .multiply(grid.commissionRate())
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return new PriceBreakdown(distance, weight, priceCents, grid.commissionRate(), commission, priceCents - commission);
    }
}
