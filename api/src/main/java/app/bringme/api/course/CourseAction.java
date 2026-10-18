package app.bringme.api.course;

/** Événements qui font évoluer une course. */
public enum CourseAction {
    PAYMENT_SUCCEEDED,
    CHECKOUT_EXPIRED,
    CANCEL,
    ACCEPT,
    DELIVER,
    PAYOUT_SUCCEEDED,
    REFUND_SUCCEEDED
}
