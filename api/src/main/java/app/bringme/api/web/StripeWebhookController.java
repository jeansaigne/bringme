package app.bringme.api.web;

import app.bringme.api.course.CourseService;
import app.bringme.api.payment.InvalidWebhookSignatureException;
import app.bringme.api.payment.PaymentEvent;
import app.bringme.api.payment.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Point d'entrée des webhooks Stripe : signature vérifiée, traitement idempotent. */
@RestController
public class StripeWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentProvider payments;
    private final CourseService courses;

    public StripeWebhookController(PaymentProvider payments, CourseService courses) {
        this.payments = payments;
        this.courses = courses;
    }

    @PostMapping(path = "/api/stripe/webhook", consumes = "application/json")
    public ResponseEntity<String> receive(
            @RequestBody byte[] payload, @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        PaymentEvent event;
        try {
            event = payments.parseWebhook(payload, signature);
        } catch (InvalidWebhookSignatureException e) {
            LOG.warn("Webhook rejeté : {}", e.getMessage());
            return ResponseEntity.badRequest().body("invalid signature");
        }
        boolean processed = courses.handle(event);
        LOG.info("Webhook {} {} : {}", event.type(), event.id(), processed ? "traité" : "doublon ignoré");
        return ResponseEntity.ok(processed ? "processed" : "duplicate");
    }
}
