package app.bringme.api.pricing;

import app.bringme.api.config.BringmeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grille du brief : base jusqu'à {@code includedKm}, puis un supplément par km commencé au-delà, plus un
 * supplément fixe selon la tranche de poids déclarée. Commission plateforme arrondie au centime le plus proche
 * (half-up). Calcul en entiers (centimes), aucune arithmétique flottante.
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
        List<BringmeProperties.WeightTier> tiers = grid.weightTiers();
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("Aucune tranche de poids configurée");
        }
        Set<String> codes = new HashSet<>();
        int previousMax = 0;
        for (BringmeProperties.WeightTier t : tiers) {
            if (!codes.add(t.code()) || t.maxKg() <= previousMax || t.surchargeCents() < 0) {
                throw new IllegalArgumentException("Tranches de poids invalides (codes uniques, bornes croissantes)");
            }
            previousMax = t.maxKg();
        }
    }

    public BringmeProperties.Pricing grid() {
        return grid;
    }

    public List<BringmeProperties.WeightTier> weightTiers() {
        return grid.weightTiers();
    }

    public Optional<BringmeProperties.WeightTier> tier(String code) {
        return grid.weightTiers().stream().filter(t -> t.code().equals(code)).findFirst();
    }

    public PriceBreakdown price(BigDecimal distanceKm, String weightTier) {
        Objects.requireNonNull(distanceKm, "distanceKm");
        if (distanceKm.signum() < 0) {
            throw new IllegalArgumentException("Distance négative");
        }
        BringmeProperties.WeightTier tier = tier(weightTier).orElseThrow(() -> new UnknownWeightTierException(weightTier));
        BigDecimal distance = distanceKm.setScale(3, RoundingMode.HALF_UP);

        long extraKm = distance.subtract(BigDecimal.valueOf(grid.includedKm()))
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        long price = grid.baseCents() + extraKm * grid.perKmCents() + tier.surchargeCents();
        int priceCents = Math.toIntExact(price);
        int commission = BigDecimal.valueOf(priceCents)
                .multiply(grid.commissionRate())
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return new PriceBreakdown(distance, tier.code(), priceCents, grid.commissionRate(), commission, priceCents - commission);
    }
}
