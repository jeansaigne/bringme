package app.bringme.api.course;

import static app.bringme.api.course.CourseAction.ACCEPT;
import static app.bringme.api.course.CourseAction.CANCEL;
import static app.bringme.api.course.CourseAction.CHECKOUT_EXPIRED;
import static app.bringme.api.course.CourseAction.DELIVER;
import static app.bringme.api.course.CourseAction.PAYMENT_SUCCEEDED;
import static app.bringme.api.course.CourseAction.PAYOUT_SUCCEEDED;
import static app.bringme.api.course.CourseAction.REFUND_SUCCEEDED;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Machine à états explicite des courses : la table ci-dessous est l'unique source de vérité.
 *
 * <pre>
 * PENDING_PAYMENT --PAYMENT_SUCCEEDED--> PAID --ACCEPT--> ACCEPTED --DELIVER--> DELIVERED --PAYOUT_SUCCEEDED--> PAID_OUT
 * PENDING_PAYMENT --CHECKOUT_EXPIRED--> EXPIRED
 * PENDING_PAYMENT --CANCEL--> CANCELLED
 * PAID --CANCEL--> REFUND_PENDING --REFUND_SUCCEEDED--> REFUNDED
 * CANCELLED | EXPIRED --PAYMENT_SUCCEEDED--> REFUND_PENDING   (paiement tardif : on rembourse)
 * </pre>
 */
public final class CourseStateMachine {

    private static final Map<CourseStatus, Map<CourseAction, CourseStatus>> TRANSITIONS = new EnumMap<>(CourseStatus.class);

    static {
        for (CourseStatus s : CourseStatus.values()) {
            TRANSITIONS.put(s, new EnumMap<>(CourseAction.class));
        }
        allow(CourseStatus.PENDING_PAYMENT, PAYMENT_SUCCEEDED, CourseStatus.PAID);
        allow(CourseStatus.PENDING_PAYMENT, CHECKOUT_EXPIRED, CourseStatus.EXPIRED);
        allow(CourseStatus.PENDING_PAYMENT, CANCEL, CourseStatus.CANCELLED);
        allow(CourseStatus.PAID, ACCEPT, CourseStatus.ACCEPTED);
        allow(CourseStatus.PAID, CANCEL, CourseStatus.REFUND_PENDING);
        allow(CourseStatus.ACCEPTED, DELIVER, CourseStatus.DELIVERED);
        allow(CourseStatus.DELIVERED, PAYOUT_SUCCEEDED, CourseStatus.PAID_OUT);
        allow(CourseStatus.REFUND_PENDING, REFUND_SUCCEEDED, CourseStatus.REFUNDED);
        allow(CourseStatus.CANCELLED, PAYMENT_SUCCEEDED, CourseStatus.REFUND_PENDING);
        allow(CourseStatus.EXPIRED, PAYMENT_SUCCEEDED, CourseStatus.REFUND_PENDING);
        TRANSITIONS.replaceAll((status, actions) -> Collections.unmodifiableMap(actions));
    }

    private CourseStateMachine() {}

    private static void allow(CourseStatus from, CourseAction action, CourseStatus to) {
        TRANSITIONS.get(from).put(action, to);
    }

    /** État suivant, ou {@link IllegalTransitionException} si l'action est interdite. */
    public static CourseStatus next(CourseStatus from, CourseAction action) {
        return tryNext(from, action).orElseThrow(() -> new IllegalTransitionException(from, action));
    }

    public static Optional<CourseStatus> tryNext(CourseStatus from, CourseAction action) {
        return Optional.ofNullable(TRANSITIONS.get(from).get(action));
    }

    public static Set<CourseAction> allowedActions(CourseStatus from) {
        return TRANSITIONS.get(from).keySet();
    }

    public static boolean isTerminal(CourseStatus status) {
        return TRANSITIONS.get(status).isEmpty();
    }
}
