package app.bringme.api.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontière avec le prestataire de paiement. Le reste de l'application ne connaît que cette interface ;
 * l'implémentation Stripe vit dans {@code payment.stripe}.
 */
public interface PaymentProvider {

    record CheckoutRequest(
            UUID courseId, int amountCents, String currency, String description, String customerEmail,
            String successUrl, String cancelUrl, Instant expiresAt) {}

    record CheckoutSession(String id, String url) {}

    record PayeeStatus(boolean transfersActive, String transfersStatus) {}

    record RefundResult(String id, boolean succeeded) {}

    record TransferRequest(UUID courseId, String payeeAccountId, int amountCents, String currency, String paymentReference) {}

    record TransferResult(String transferId, String chargeId) {}

    CheckoutSession createCheckout(CheckoutRequest request);

    /** Tente d'expirer une session de paiement ouverte. Renvoie false si elle n'est plus ouverte. */
    boolean expireCheckout(String sessionId);

    /** Crée le compte bénéficiaire (coursier) ; idempotent par utilisateur. */
    String createPayeeAccount(UUID userId, String email, String displayName);

    /** Secret de session pour le composant d'onboarding intégré côté front. */
    String createOnboardingSession(String payeeAccountId);

    /** Lien à usage unique vers le tableau de bord des gains du bénéficiaire. */
    String createPayeeDashboardLink(String payeeAccountId);

    PayeeStatus payeeStatus(String payeeAccountId);

    /** Verse au bénéficiaire sa part d'un paiement encaissé ; idempotent par course. */
    TransferResult transfer(TransferRequest request);

    /** Remboursement intégral ; idempotent par course. */
    RefundResult refund(UUID courseId, String paymentReference);

    /** Vérifie la signature puis traduit la notification en événement métier. */
    PaymentEvent parseWebhook(byte[] payload, String signatureHeader);
}
