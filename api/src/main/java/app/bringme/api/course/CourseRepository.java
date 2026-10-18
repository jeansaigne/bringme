package app.bringme.api.course;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CourseRepository {

    /** Valeur spéciale : la colonne reçoit {@code now()} côté base. */
    public static final Object NOW = new Object();

    private static final Set<String> UPDATABLE_COLUMNS = Set.of(
            "courier_id", "stripe_payment_intent_id", "stripe_charge_id", "stripe_transfer_id", "stripe_refund_id",
            "payout_error", "paid_at", "accepted_at", "delivered_at", "paid_out_at", "closed_at");

    private final JdbcClient jdbc;

    public CourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Course insert(NewCourse c) {
        return jdbc.sql("""
                insert into public.courses (client_id, item_description,
                    pickup_address, pickup_lat, pickup_lng, dropoff_address, dropoff_lat, dropoff_lng,
                    weight_kg, distance_km, price_cents, commission_rate, commission_cents, courier_amount_cents)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning *
                """)
                .params(c.clientId(), c.itemDescription(),
                        c.pickup().label(), c.pickup().lat(), c.pickup().lng(),
                        c.dropoff().label(), c.dropoff().lat(), c.dropoff().lng(),
                        c.price().weightKg(), c.price().distanceKm(), c.price().priceCents(), c.price().commissionRate(),
                        c.price().commissionCents(), c.price().courierAmountCents())
                .query(CourseRepository::map)
                .single();
    }

    public Optional<Course> findById(UUID id) {
        return jdbc.sql("select * from public.courses where id = ?").param(id).query(CourseRepository::map).optional();
    }

    public Optional<Course> findByPaymentIntent(String paymentIntentId) {
        return jdbc.sql("select * from public.courses where stripe_payment_intent_id = ?")
                .param(paymentIntentId)
                .query(CourseRepository::map)
                .optional();
    }

    public List<Course> findForUser(UUID userId) {
        return jdbc.sql("""
                select * from public.courses where client_id = ? or courier_id = ?
                order by created_at desc limit 50
                """)
                .params(userId, userId)
                .query(CourseRepository::map)
                .list();
    }

    public List<Course> findAvailable(UUID excludedClientId) {
        return jdbc.sql("""
                select * from public.courses
                where status = 'PAID' and courier_id is null and client_id <> ?
                order by created_at limit 50
                """)
                .param(excludedClientId)
                .query(CourseRepository::map)
                .list();
    }

    public void saveCheckoutSession(UUID id, String sessionId) {
        jdbc.sql("update public.courses set stripe_checkout_session_id = ? where id = ?").params(sessionId, id).update();
    }

    public void savePayoutError(UUID id, String error) {
        jdbc.sql("update public.courses set payout_error = ? where id = ?").params(error, id).update();
    }

    public void saveRefundId(UUID id, String refundId) {
        jdbc.sql("update public.courses set stripe_refund_id = ? where id = ?").params(refundId, id).update();
    }

    public int incrementHandoverAttempts(UUID id) {
        return jdbc.sql("update public.courses set handover_attempts = handover_attempts + 1 where id = ? returning handover_attempts")
                .param(id)
                .query(Integer.class)
                .single();
    }

    /**
     * Applique une transition validée par la machine à états. Verrou optimiste sur {@code version} et sur
     * le statut lu : renvoie {@code false} si la ligne a changé entre-temps.
     */
    public boolean transition(Course course, CourseStatus to, String actor, Map<String, Object> changes) {
        StringBuilder sql = new StringBuilder(
                "update public.courses set status = ?::public.course_status, version = version + 1");
        List<Object> params = new ArrayList<>();
        params.add(to.name());
        for (Map.Entry<String, Object> change : new LinkedHashMap<>(changes).entrySet()) {
            if (!UPDATABLE_COLUMNS.contains(change.getKey())) {
                throw new IllegalArgumentException("Colonne non modifiable : " + change.getKey());
            }
            if (change.getValue() == NOW) {
                sql.append(", ").append(change.getKey()).append(" = now()");
            } else {
                sql.append(", ").append(change.getKey()).append(" = ?");
                params.add(change.getValue());
            }
        }
        sql.append(" where id = ? and version = ? and status = ?::public.course_status");
        params.add(course.id());
        params.add(course.version());
        params.add(course.status().name());
        int updated = jdbc.sql(sql.toString()).params(params).update();
        if (updated == 1) {
            logTransition(course.id(), course.status(), to, actor);
        }
        return updated == 1;
    }

    public void logTransition(UUID courseId, CourseStatus from, CourseStatus to, String actor) {
        jdbc.sql("""
                insert into public.course_transitions (course_id, from_status, to_status, actor)
                values (?, ?::public.course_status, ?::public.course_status, ?)
                """)
                .params(courseId, from == null ? null : from.name(), to.name(), actor)
                .update();
    }

    public void saveHandoverCode(UUID courseId, String code) {
        jdbc.sql("insert into public.course_handover_codes (course_id, code) values (?, ?) on conflict (course_id) do nothing")
                .params(courseId, code)
                .update();
    }

    public Optional<String> findHandoverCode(UUID courseId) {
        return jdbc.sql("select code from public.course_handover_codes where course_id = ?")
                .param(courseId)
                .query(String.class)
                .optional();
    }

    private static Course map(ResultSet rs, int rowNum) throws SQLException {
        return new Course(
                rs.getObject("id", UUID.class),
                rs.getObject("client_id", UUID.class),
                rs.getObject("courier_id", UUID.class),
                rs.getString("item_description"),
                rs.getString("pickup_address"),
                rs.getDouble("pickup_lat"),
                rs.getDouble("pickup_lng"),
                rs.getString("dropoff_address"),
                rs.getDouble("dropoff_lat"),
                rs.getDouble("dropoff_lng"),
                rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("distance_km"),
                rs.getInt("price_cents"),
                rs.getBigDecimal("commission_rate"),
                rs.getInt("commission_cents"),
                rs.getInt("courier_amount_cents"),
                rs.getString("currency"),
                CourseStatus.valueOf(rs.getString("status")),
                rs.getInt("handover_attempts"),
                rs.getString("stripe_checkout_session_id"),
                rs.getString("stripe_payment_intent_id"),
                rs.getString("stripe_charge_id"),
                rs.getString("stripe_transfer_id"),
                rs.getString("stripe_refund_id"),
                rs.getString("payout_error"),
                rs.getInt("version"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("paid_at", OffsetDateTime.class),
                rs.getObject("accepted_at", OffsetDateTime.class),
                rs.getObject("delivered_at", OffsetDateTime.class),
                rs.getObject("paid_out_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class));
    }
}
