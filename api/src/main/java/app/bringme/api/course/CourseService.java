package app.bringme.api.course;

import app.bringme.api.config.ApiException;
import app.bringme.api.config.BringmeProperties;
import app.bringme.api.geo.GeoPoint;
import app.bringme.api.geo.Geocoder;
import app.bringme.api.payment.PaymentEvent;
import app.bringme.api.payment.PaymentProvider;
import app.bringme.api.payment.PaymentProviderException;
import app.bringme.api.pricing.PriceBreakdown;
import app.bringme.api.pricing.PricingPolicy;
import app.bringme.api.profile.Profile;
import app.bringme.api.profile.ProfileRepository;
import app.bringme.api.security.CurrentUser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Cas d'usage d'une course. Les règles d'état sont déléguées à {@link CourseStateMachine}. */
@Service
public class CourseService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseService.class);
    private static final String SYSTEM = "system";
    private static final String STRIPE = "stripe";
    private static final Duration CHECKOUT_TTL = Duration.ofMinutes(30);

    public record Quote(GeoPoint pickup, GeoPoint dropoff, PriceBreakdown price) {}

    public record CreatedCourse(Course course, String checkoutUrl) {}

    private final CourseRepository courses;
    private final ProfileRepository profiles;
    private final StripeEventRepository events;
    private final PaymentProvider payments;
    private final Geocoder geocoder;
    private final PricingPolicy pricing;
    private final TransactionTemplate tx;
    private final BringmeProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CourseService(CourseRepository courses, ProfileRepository profiles, StripeEventRepository events, PaymentProvider payments,
            Geocoder geocoder, PricingPolicy pricing, TransactionTemplate tx, BringmeProperties props, Clock clock) {
        this.courses = courses;
        this.profiles = profiles;
        this.events = events;
        this.payments = payments;
        this.geocoder = geocoder;
        this.pricing = pricing;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ devis et création

    public Quote quote(String pickupAddress, String dropoffAddress, BigDecimal weightKg) {
        GeoPoint pickup = geocoder.geocode(pickupAddress);
        GeoPoint dropoff = geocoder.geocode(dropoffAddress);
        BigDecimal distance = BigDecimal.valueOf(pickup.distanceKmTo(dropoff));
        return new Quote(pickup, dropoff, pricing.price(distance, weightKg));
    }

    public CreatedCourse create(CurrentUser user, String item, String pickupAddress, String dropoffAddress, BigDecimal weightKg) {
        profiles.getOrCreate(user.id(), user.email());
        Quote quote = quote(pickupAddress, dropoffAddress, weightKg);
        Course course = tx.execute(s -> {
            Course c = courses.insert(new NewCourse(user.id(), item, quote.pickup(), quote.dropoff(), quote.price()));
            courses.logTransition(c.id(), null, c.status(), user.id().toString());
            return c;
        });
        // Le front est une SPA en hash routing (hébergement statique sans règle de réécriture).
        String base = props.webUrl() + "/#/courses/" + course.id();
        PaymentProvider.CheckoutSession session;
        try {
            session = payments.createCheckout(new PaymentProvider.CheckoutRequest(
                    course.id(), course.priceCents(), course.currency(), "Course BringMe : " + abbreviate(item),
                    user.email(), base + "?paid=1", base, clock.instant().plus(CHECKOUT_TTL)));
        } catch (RuntimeException e) {
            transition(course, CourseAction.CANCEL, SYSTEM, Map.of("closed_at", CourseRepository.NOW));
            throw e;
        }
        courses.saveCheckoutSession(course.id(), session.id());
        LOG.info("Course {} créée ({} cts), session de paiement {}", course.id(), course.priceCents(), session.id());
        return new CreatedCourse(course, session.url());
    }

    // ------------------------------------------------------------------ lecture

    public Course get(CurrentUser user, UUID id) {
        Course c = courses.findById(id).orElseThrow(ApiException::notFound);
        boolean openOffer = c.status() == CourseStatus.PAID && c.courierId() == null;
        if (!c.isClient(user.id()) && !c.isCourier(user.id()) && !openOffer) {
            throw ApiException.notFound();
        }
        return c;
    }

    public List<Course> mine(CurrentUser user) {
        return courses.findForUser(user.id());
    }

    public List<Course> available(CurrentUser user) {
        return courses.findAvailable(user.id());
    }

    /** Le code n'est visible que du client, tant que la remise n'a pas eu lieu. */
    public String handoverCodeFor(CurrentUser user, Course c) {
        if (!c.isClient(user.id()) || (c.status() != CourseStatus.PAID && c.status() != CourseStatus.ACCEPTED)) {
            return null;
        }
        return courses.findHandoverCode(c.id()).orElse(null);
    }

    // ------------------------------------------------------------------ actions utilisateur

    public Course cancel(CurrentUser user, UUID id) {
        Course c = courses.findById(id).orElseThrow(ApiException::notFound);
        if (!c.isClient(user.id())) {
            throw ApiException.forbidden("Seul le client peut annuler sa course");
        }
        return switch (c.status()) {
            case PENDING_PAYMENT -> {
                if (c.stripeCheckoutSessionId() != null) {
                    payments.expireCheckout(c.stripeCheckoutSessionId());
                }
                yield transition(c, CourseAction.CANCEL, user.id().toString(), Map.of("closed_at", CourseRepository.NOW));
            }
            case PAID -> refund(transition(c, CourseAction.CANCEL, user.id().toString(), Map.of()));
            case REFUND_PENDING -> refund(c);
            default -> throw new IllegalTransitionException(c.status(), CourseAction.CANCEL);
        };
    }

    public Course accept(CurrentUser user, UUID id) {
        Course c = courses.findById(id).orElseThrow(ApiException::notFound);
        CourseStateMachine.next(c.status(), CourseAction.ACCEPT);
        if (c.isClient(user.id())) {
            throw ApiException.forbidden("Vous ne pouvez pas accepter votre propre course");
        }
        Profile courier = profiles.getOrCreate(user.id(), user.email());
        if (courier.stripeAccountId() == null) {
            throw ApiException.conflict("payee_missing", "Terminez d'abord votre inscription coursier");
        }
        PaymentProvider.PayeeStatus status = payments.payeeStatus(courier.stripeAccountId());
        if (!status.transfersActive()) {
            throw ApiException.conflict("payee_not_ready",
                    "Votre compte coursier n'est pas encore habilité à recevoir des virements (" + status.transfersStatus() + ")");
        }
        return transition(c, CourseAction.ACCEPT, user.id().toString(), Map.of("courier_id", user.id(), "accepted_at", CourseRepository.NOW));
    }

    public Course deliver(CurrentUser user, UUID id, String code) {
        Course c = courses.findById(id).orElseThrow(ApiException::notFound);
        if (!c.isCourier(user.id())) {
            throw ApiException.forbidden("Seul le coursier de cette course peut confirmer la livraison");
        }
        CourseStateMachine.next(c.status(), CourseAction.DELIVER);
        int max = props.handover().maxAttempts();
        if (c.handoverAttempts() >= max) {
            throw new ApiException(HttpStatus.LOCKED, "handover_locked", "Trop d'essais : contactez le support");
        }
        String expected = courses.findHandoverCode(c.id()).orElseThrow(() -> new IllegalStateException("Code de remise absent"));
        if (code == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), code.trim().getBytes(StandardCharsets.UTF_8))) {
            int attempts = courses.incrementHandoverAttempts(c.id());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "handover_code_invalid",
                    "Code incorrect (" + Math.max(0, max - attempts) + " essai(s) restant(s))");
        }
        Course delivered = transition(c, CourseAction.DELIVER, user.id().toString(), Map.of("delivered_at", CourseRepository.NOW));
        return payout(delivered);
    }

    public Course retryPayout(CurrentUser user, UUID id) {
        Course c = courses.findById(id).orElseThrow(ApiException::notFound);
        if (!c.isCourier(user.id()) && !c.isClient(user.id())) {
            throw ApiException.notFound();
        }
        CourseStateMachine.next(c.status(), CourseAction.PAYOUT_SUCCEEDED);
        return payout(c);
    }

    // ------------------------------------------------------------------ argent

    /** Transfère au coursier sa part, après avoir revérifié que son compte est habilité. */
    private Course payout(Course c) {
        Profile courier = profiles.getOrCreate(c.courierId(), null);
        try {
            if (courier.stripeAccountId() == null) {
                throw new PaymentProviderException("Compte coursier absent");
            }
            PaymentProvider.PayeeStatus status = payments.payeeStatus(courier.stripeAccountId());
            if (!status.transfersActive()) {
                throw new PaymentProviderException("Compte coursier non habilité aux transferts (" + status.transfersStatus() + ")");
            }
            PaymentProvider.TransferResult t = payments.transfer(new PaymentProvider.TransferRequest(
                    c.id(), courier.stripeAccountId(), c.courierAmountCents(), c.currency(), c.stripePaymentIntentId()));
            Map<String, Object> changes = new HashMap<>();
            changes.put("stripe_transfer_id", t.transferId());
            changes.put("stripe_charge_id", t.chargeId());
            changes.put("paid_out_at", CourseRepository.NOW);
            changes.put("payout_error", null);
            LOG.info("Course {} : transfert {} de {} cts au coursier", c.id(), t.transferId(), c.courierAmountCents());
            return transition(c, CourseAction.PAYOUT_SUCCEEDED, SYSTEM, changes);
        } catch (PaymentProviderException e) {
            LOG.warn("Course {} : transfert en échec : {}", c.id(), e.getMessage());
            courses.savePayoutError(c.id(), e.getMessage());
            return courses.findById(c.id()).orElseThrow();
        }
    }

    /** Rembourse intégralement une course en REFUND_PENDING. */
    private Course refund(Course c) {
        if (c.stripePaymentIntentId() == null) {
            throw new IllegalStateException("Course " + c.id() + " sans paiement à rembourser");
        }
        PaymentProvider.RefundResult r = payments.refund(c.id(), c.stripePaymentIntentId());
        courses.saveRefundId(c.id(), r.id());
        Course fresh = courses.findById(c.id()).orElseThrow();
        if (r.succeeded() && fresh.status() == CourseStatus.REFUND_PENDING) {
            LOG.info("Course {} remboursée ({})", c.id(), r.id());
            return transition(fresh, CourseAction.REFUND_SUCCEEDED, SYSTEM, Map.of("closed_at", CourseRepository.NOW));
        }
        return fresh;
    }

    // ------------------------------------------------------------------ webhooks

    /**
     * Traite une notification de paiement. L'enregistrement de l'événement et la transition se font dans la
     * même transaction : un doublon est sans effet, un échec est rejoué par Stripe.
     *
     * @return {@code false} si l'événement avait déjà été traité.
     */
    public boolean handle(PaymentEvent event) {
        Boolean processed = tx.execute(status -> {
            if (!events.tryRecord(event.id(), event.type())) {
                return false;
            }
            apply(event);
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }

    private void apply(PaymentEvent event) {
        switch (event) {
            case PaymentEvent.CheckoutCompleted e when e.paid() -> onPaymentSucceeded(e);
            case PaymentEvent.CheckoutCompleted e -> LOG.info("Session de la course {} complétée sans paiement immédiat", e.courseId());
            case PaymentEvent.CheckoutExpired e -> courses.findById(e.courseId()).ifPresent(c ->
                    CourseStateMachine.tryNext(c.status(), CourseAction.CHECKOUT_EXPIRED).ifPresent(to ->
                            transition(c, CourseAction.CHECKOUT_EXPIRED, STRIPE, Map.of("closed_at", CourseRepository.NOW))));
            case PaymentEvent.PaymentRefunded e when e.fullyRefunded() && e.paymentReference() != null ->
                    courses.findByPaymentIntent(e.paymentReference()).ifPresent(c ->
                            CourseStateMachine.tryNext(c.status(), CourseAction.REFUND_SUCCEEDED).ifPresent(to ->
                                    transition(c, CourseAction.REFUND_SUCCEEDED, STRIPE, Map.of("closed_at", CourseRepository.NOW))));
            case PaymentEvent.PaymentRefunded e -> LOG.info("Remboursement partiel ignoré ({})", e.id());
            case PaymentEvent.Ignored e -> LOG.debug("Événement {} ignoré", e.type());
        }
    }

    private void onPaymentSucceeded(PaymentEvent.CheckoutCompleted e) {
        Course c = courses.findById(e.courseId()).orElse(null);
        if (c == null) {
            LOG.warn("Paiement reçu pour une course inconnue {}", e.courseId());
            return;
        }
        CourseStatus to = CourseStateMachine.tryNext(c.status(), CourseAction.PAYMENT_SUCCEEDED).orElse(null);
        if (to == null) {
            LOG.info("Course {} déjà en {}, paiement ignoré", c.id(), c.status());
            return;
        }
        Map<String, Object> changes = new HashMap<>();
        changes.put("stripe_payment_intent_id", e.paymentReference());
        if (to == CourseStatus.PAID) {
            changes.put("paid_at", CourseRepository.NOW);
        }
        Course updated = transition(c, CourseAction.PAYMENT_SUCCEEDED, STRIPE, changes);
        if (to == CourseStatus.PAID) {
            courses.saveHandoverCode(c.id(), String.format("%06d", random.nextInt(1_000_000)));
            LOG.info("Course {} payée", c.id());
        } else {
            LOG.warn("Paiement tardif sur la course {} ({}) : remboursement après commit", c.id(), c.status());
            afterCommit(() -> {
                try {
                    refund(updated);
                } catch (RuntimeException ex) {
                    LOG.error("Remboursement du paiement tardif de la course {} en échec", c.id(), ex);
                }
            });
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private Course transition(Course c, CourseAction action, String actor, Map<String, Object> changes) {
        CourseStatus to = CourseStateMachine.next(c.status(), action);
        Course updated = tx.execute(s -> {
            if (!courses.transition(c, to, actor, changes)) {
                throw new StaleCourseException(c.id());
            }
            return courses.findById(c.id()).orElseThrow();
        });
        LOG.info("Course {} : {} --{}--> {} (par {})", c.id(), c.status(), action, to, actor);
        return updated;
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String abbreviate(String s) {
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }
}
