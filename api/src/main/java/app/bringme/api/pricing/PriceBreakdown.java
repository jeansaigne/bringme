package app.bringme.api.pricing;

import java.math.BigDecimal;

/** Résultat du calcul de prix. Invariant : priceCents = commissionCents + courierAmountCents. */
public record PriceBreakdown(
        BigDecimal distanceKm,
        String sizeTier,
        int priceCents,
        BigDecimal commissionRate,
        int commissionCents,
        int courierAmountCents) {}
