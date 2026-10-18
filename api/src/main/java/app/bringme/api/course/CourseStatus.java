package app.bringme.api.course;

/** États d'une course. Doit rester aligné avec l'enum Postgres {@code public.course_status}. */
public enum CourseStatus {
    PENDING_PAYMENT,
    PAID,
    ACCEPTED,
    DELIVERED,
    PAID_OUT,
    CANCELLED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED
}
