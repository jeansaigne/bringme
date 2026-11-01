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
 * Prix d'une course : forfait du gabarit jusqu'à {@code includedKm}, puis le prix au km du gabarit par km
 * commencé au-delà. Commission plateforme arrondie au centime le plus proche (half-up). Calcul en entiers
 * (centimes), aucune arithmétique flottante.
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
        if (grid.includedKm() < 0) {
            throw new IllegalArgumentException("Distance incluse négative");
        }
        List<BringmeProperties.SizeTier> tiers = grid.sizeTiers();
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("Aucun gabarit configuré");
        }
        Set<String> codes = new HashSet<>();
        for (BringmeProperties.SizeTier t : tiers) {
            if (!codes.add(t.code()) || t.requiredVehicle() == null || t.baseCents() <= 0 || t.perKmCents() < 0) {
                throw new IllegalArgumentException("Gabarit invalide : " + t.code());
            }
        }
    }

    public BringmeProperties.Pricing grid() {
        return grid;
    }

    public Optional<BringmeProperties.SizeTier> tier(String code) {
        return grid.sizeTiers().stream().filter(t -> t.code().equals(code)).findFirst();
    }

    /** Codes des gabarits qu'un véhicule peut transporter. */
    public List<String> tiersCarriedBy(Vehicle vehicle) {
        return grid.sizeTiers().stream().filter(t -> vehicle.canCarry(t.requiredVehicle())).map(BringmeProperties.SizeTier::code).toList();
    }

    public boolean canCarry(Vehicle vehicle, String sizeTier) {
        return vehicle != null && tier(sizeTier).map(t -> vehicle.canCarry(t.requiredVehicle())).orElse(false);
    }

    public PriceBreakdown price(BigDecimal distanceKm, String sizeTier) {
        Objects.requireNonNull(distanceKm, "distanceKm");
        if (distanceKm.signum() < 0) {
            throw new IllegalArgumentException("Distance négative");
        }
        BringmeProperties.SizeTier tier = tier(sizeTier).orElseThrow(() -> new UnknownSizeTierException(sizeTier));
        BigDecimal distance = distanceKm.setScale(3, RoundingMode.HALF_UP);

        long extraKm = distance.subtract(BigDecimal.valueOf(grid.includedKm()))
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        int priceCents = Math.toIntExact(tier.baseCents() + extraKm * tier.perKmCents());
        int commission = BigDecimal.valueOf(priceCents)
                .multiply(grid.commissionRate())
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return new PriceBreakdown(distance, tier.code(), priceCents, grid.commissionRate(), commission, priceCents - commission);
    }
}
