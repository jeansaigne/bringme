package app.bringme.api.pricing;

import java.math.BigDecimal;

/** Résultat du calcul de prix. Invariant : priceCents = commissionCents + courierAmountCents. */
public record PriceBreakdown(
        BigDecimal distanceKm,
        BigDecimal weightKg,
        int priceCents,
        BigDecimal commissionRate,
        int commissionCents,
        int courierAmountCents) {}
