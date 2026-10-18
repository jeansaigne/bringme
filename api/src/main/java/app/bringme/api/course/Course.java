package app.bringme.api.course;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ligne de la table {@code public.courses}. */
public record Course(
        UUID id,
        UUID clientId,
        UUID courierId,
        String itemDescription,
        String pickupAddress,
        double pickupLat,
        double pickupLng,
        String dropoffAddress,
        double dropoffLat,
        double dropoffLng,
        BigDecimal weightKg,
        BigDecimal distanceKm,
        int priceCents,
        BigDecimal commissionRate,
        int commissionCents,
        int courierAmountCents,
        String currency,
        CourseStatus status,
        int handoverAttempts,
        String stripeCheckoutSessionId,
        String stripePaymentIntentId,
        String stripeChargeId,
        String stripeTransferId,
        String stripeRefundId,
        String payoutError,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime paidAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime paidOutAt,
        OffsetDateTime closedAt) {

    public boolean isClient(UUID userId) {
        return clientId.equals(userId);
    }

    public boolean isCourier(UUID userId) {
        return courierId != null && courierId.equals(userId);
    }
}
