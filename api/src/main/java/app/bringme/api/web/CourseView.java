package app.bringme.api.web;

import app.bringme.api.course.Course;
import app.bringme.api.course.CourseStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Vue d'une course, relative à l'utilisateur qui la consulte (rôle, actions possibles, code de remise). */
public record CourseView(
        UUID id,
        CourseStatus status,
        String role,
        List<String> actions,
        String itemDescription,
        String pickupAddress,
        String dropoffAddress,
        BigDecimal weightKg,
        BigDecimal distanceKm,
        int priceCents,
        BigDecimal commissionRate,
        int commissionCents,
        int courierAmountCents,
        String currency,
        String handoverCode,
        String payoutError,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime paidOutAt,
        OffsetDateTime closedAt) {

    public static CourseView of(Course c, UUID viewer, String handoverCode) {
        String role = c.isClient(viewer) ? "client" : c.isCourier(viewer) ? "courier" : "candidate";
        List<String> actions = new ArrayList<>();
        switch (role) {
            case "client" -> {
                if (c.status() == CourseStatus.PENDING_PAYMENT || c.status() == CourseStatus.PAID
                        || c.status() == CourseStatus.REFUND_PENDING) {
                    actions.add("cancel");
                }
                if (c.status() == CourseStatus.DELIVERED) {
                    actions.add("retry-payout");
                }
            }
            case "courier" -> {
                if (c.status() == CourseStatus.ACCEPTED) {
                    actions.add("deliver");
                }
                if (c.status() == CourseStatus.DELIVERED) {
                    actions.add("retry-payout");
                }
            }
            default -> {
                if (c.status() == CourseStatus.PAID && c.courierId() == null) {
                    actions.add("accept");
                }
            }
        }
        return new CourseView(c.id(), c.status(), role, List.copyOf(actions), c.itemDescription(), c.pickupAddress(),
                c.dropoffAddress(), c.weightKg(), c.distanceKm(), c.priceCents(), c.commissionRate(), c.commissionCents(),
                c.courierAmountCents(), c.currency(), handoverCode, c.payoutError(), c.createdAt(), c.paidAt(), c.acceptedAt(),
                c.deliveredAt(), c.paidOutAt(), c.closedAt());
    }
}
