package app.bringme.api.payment;

import java.util.UUID;

/** Notification du prestataire de paiement, déjà authentifiée et traduite. */
public sealed interface PaymentEvent {

    String id();

    String type();

    record CheckoutCompleted(String id, String type, UUID courseId, String paymentReference, boolean paid)
            implements PaymentEvent {}

    record CheckoutExpired(String id, String type, UUID courseId) implements PaymentEvent {}

    record PaymentRefunded(String id, String type, String paymentReference, boolean fullyRefunded) implements PaymentEvent {}

    record Ignored(String id, String type) implements PaymentEvent {}
}
