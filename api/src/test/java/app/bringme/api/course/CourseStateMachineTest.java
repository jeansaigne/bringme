package app.bringme.api.course;

import static app.bringme.api.course.CourseAction.ACCEPT;
import static app.bringme.api.course.CourseAction.CANCEL;
import static app.bringme.api.course.CourseAction.CHECKOUT_EXPIRED;
import static app.bringme.api.course.CourseAction.DELIVER;
import static app.bringme.api.course.CourseAction.PAYMENT_SUCCEEDED;
import static app.bringme.api.course.CourseAction.PAYOUT_SUCCEEDED;
import static app.bringme.api.course.CourseAction.REFUND_SUCCEEDED;
import static app.bringme.api.course.CourseStatus.ACCEPTED;
import static app.bringme.api.course.CourseStatus.CANCELLED;
import static app.bringme.api.course.CourseStatus.DELIVERED;
import static app.bringme.api.course.CourseStatus.EXPIRED;
import static app.bringme.api.course.CourseStatus.PAID;
import static app.bringme.api.course.CourseStatus.PAID_OUT;
import static app.bringme.api.course.CourseStatus.PENDING_PAYMENT;
import static app.bringme.api.course.CourseStatus.REFUNDED;
import static app.bringme.api.course.CourseStatus.REFUND_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CourseStateMachineTest {

    /** Table attendue, écrite indépendamment de l'implémentation. */
    private static final Map<String, CourseStatus> EXPECTED = Map.ofEntries(
            Map.entry(key(PENDING_PAYMENT, PAYMENT_SUCCEEDED), PAID),
            Map.entry(key(PENDING_PAYMENT, CHECKOUT_EXPIRED), EXPIRED),
            Map.entry(key(PENDING_PAYMENT, CANCEL), CANCELLED),
            Map.entry(key(PAID, ACCEPT), ACCEPTED),
            Map.entry(key(PAID, CANCEL), REFUND_PENDING),
            Map.entry(key(ACCEPTED, DELIVER), DELIVERED),
            Map.entry(key(DELIVERED, PAYOUT_SUCCEEDED), PAID_OUT),
            Map.entry(key(REFUND_PENDING, REFUND_SUCCEEDED), REFUNDED),
            Map.entry(key(CANCELLED, PAYMENT_SUCCEEDED), REFUND_PENDING),
            Map.entry(key(EXPIRED, PAYMENT_SUCCEEDED), REFUND_PENDING));

    private static String key(CourseStatus s, CourseAction a) {
        return s + "/" + a;
    }

    static Stream<Arguments> everyPair() {
        return Stream.of(CourseStatus.values())
                .flatMap(s -> Stream.of(CourseAction.values()).map(a -> Arguments.of(s, a)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("everyPair")
    void matchesTheTransitionTableExactly(CourseStatus from, CourseAction action) {
        CourseStatus expected = EXPECTED.get(key(from, action));
        if (expected == null) {
            assertThat(CourseStateMachine.tryNext(from, action)).isEmpty();
            assertThatThrownBy(() -> CourseStateMachine.next(from, action))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining(from.name());
        } else {
            assertThat(CourseStateMachine.next(from, action)).isEqualTo(expected);
        }
    }

    @Test
    void happyPathReachesPaidOut() {
        CourseStatus s = PENDING_PAYMENT;
        for (CourseAction a : new CourseAction[] {PAYMENT_SUCCEEDED, ACCEPT, DELIVER, PAYOUT_SUCCEEDED}) {
            s = CourseStateMachine.next(s, a);
        }
        assertThat(s).isEqualTo(PAID_OUT);
    }

    @Test
    void cancellationAfterAcceptanceIsForbidden() {
        assertThatThrownBy(() -> CourseStateMachine.next(ACCEPTED, CANCEL)).isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> CourseStateMachine.next(DELIVERED, CANCEL)).isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void finalStatesAreTerminal() {
        assertThat(CourseStateMachine.isTerminal(PAID_OUT)).isTrue();
        assertThat(CourseStateMachine.isTerminal(REFUNDED)).isTrue();
        assertThat(CourseStateMachine.isTerminal(PAID)).isFalse();
        assertThat(CourseStateMachine.allowedActions(PAID)).containsExactlyInAnyOrder(ACCEPT, CANCEL);
    }
}
