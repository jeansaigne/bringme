package app.bringme.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.bringme.api.payment.stripe.StripeSignatureVerifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Webhooks Stripe de bout en bout : vraie base Postgres (migrations du repo appliquées), connexion avec le rôle
 * applicatif {@code bringme_api} (donc RLS et droits réels), signature HMAC réelle.
 */
@SpringBootTest(properties = {
    "bringme.stripe.webhook-secret=" + StripeWebhookIntegrationTest.SECRET,
    "bringme.stripe.secret-key=",
})
@AutoConfigureMockMvc
@Testcontainers
class StripeWebhookIntegrationTest {

    static final String SECRET = "whsec_integration";
    private static final String API_PASSWORD = "api-test-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        bootstrapDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "bringme_api");
        registry.add("spring.datasource.password", () -> API_PASSWORD);
    }

    private static boolean bootstrapped;

    private static synchronized void bootstrapDatabase() {
        if (bootstrapped) {
            return;
        }
        POSTGRES.start();
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = admin.createStatement()) {
            st.execute(Files.readString(Path.of("src/test/resources/supabase-shim.sql")));
            try (Stream<Path> files = Files.list(Path.of("../supabase/migrations"))) {
                for (Path migration : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                    st.execute(Files.readString(migration));
                }
            }
            st.execute("alter role bringme_api password '" + API_PASSWORD + "'");
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Initialisation de la base de test impossible", e);
        }
        bootstrapped = true;
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private final StripeSignatureVerifier signer = new StripeSignatureVerifier(SECRET, 300, Clock.systemUTC());

    private UUID clientId;

    @BeforeEach
    void createClient() throws SQLException {
        clientId = UUID.randomUUID();
        // auth.users n'est pas accessible au rôle applicatif : on passe par l'administrateur, comme Supabase Auth.
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = admin.createStatement()) {
            st.execute("insert into auth.users (id, email) values ('" + clientId + "', 'client-" + clientId + "@test.dev')");
        }
    }

    @Test
    void signupTriggerCreatesProfile() {
        assertThat(jdbc.sql("select count(*) from public.profiles where id = ?").param(clientId).query(Integer.class).single())
                .isEqualTo(1);
    }

    @Test
    void paymentMovesCourseToPaidAndIssuesHandoverCode() throws Exception {
        UUID course = insertCourse("PENDING_PAYMENT", null);

        send(completed("evt_paid_" + course, course, "pi_" + course)).andExpect(status().isOk()).andExpect(content().string("processed"));

        assertThat(statusOf(course)).isEqualTo("PAID");
        assertThat(jdbc.sql("select stripe_payment_intent_id from public.courses where id = ?").param(course).query(String.class).single())
                .isEqualTo("pi_" + course);
        assertThat(handoverCode(course)).matches("\\d{6}");
        assertThat(transitions(course)).containsExactly("PENDING_PAYMENT->PAID:stripe");
    }

    @Test
    void duplicateDeliveryIsIdempotent() throws Exception {
        UUID course = insertCourse("PENDING_PAYMENT", null);
        String event = completed("evt_dup_" + course, course, "pi_dup_" + course);

        send(event).andExpect(status().isOk()).andExpect(content().string("processed"));
        String codeAfterFirst = handoverCode(course);
        send(event).andExpect(status().isOk()).andExpect(content().string("duplicate"));

        assertThat(statusOf(course)).isEqualTo("PAID");
        assertThat(transitions(course)).hasSize(1);
        assertThat(handoverCode(course)).isEqualTo(codeAfterFirst);
    }

    @Test
    void invalidSignatureIsRejectedAndNotRecorded() throws Exception {
        UUID course = insertCourse("PENDING_PAYMENT", null);
        String eventId = "evt_forged_" + course;
        byte[] payload = completed(eventId, course, "pi_x").getBytes(StandardCharsets.UTF_8);
        String forged = new StripeSignatureVerifier("whsec_attacker", 300, Clock.systemUTC()).sign(payload, Instant.now().getEpochSecond());

        mvc.perform(post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", forged).content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(course)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.sql("select count(*) from public.stripe_events where event_id = ?").param(eventId).query(Integer.class).single())
                .isZero();
    }

    @Test
    void expirationClosesAnUnpaidCourse() throws Exception {
        UUID course = insertCourse("PENDING_PAYMENT", null);
        send(expired("evt_exp_" + course, course)).andExpect(status().isOk());
        assertThat(statusOf(course)).isEqualTo("EXPIRED");
    }

    @Test
    void outOfOrderExpirationDoesNotUndoAPayment() throws Exception {
        UUID course = insertCourse("PENDING_PAYMENT", null);
        send(completed("evt_p_" + course, course, "pi_ooo_" + course)).andExpect(status().isOk());
        send(expired("evt_e_" + course, course)).andExpect(status().isOk()).andExpect(content().string("processed"));
        assertThat(statusOf(course)).isEqualTo("PAID");
    }

    @Test
    void refundNotificationClosesARefundPendingCourse() throws Exception {
        UUID course = insertCourse("REFUND_PENDING", "pi_ref_" + UUID.randomUUID());
        String pi = jdbc.sql("select stripe_payment_intent_id from public.courses where id = ?").param(course).query(String.class).single();
        send(envelope("evt_ref_" + course, "charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"" + pi + "\",\"refunded\":true}"))
                .andExpect(status().isOk());
        assertThat(statusOf(course)).isEqualTo("REFUNDED");
    }

    @Test
    void latePaymentOnCancelledCourseRequiresRefund() throws Exception {
        UUID course = insertCourse("CANCELLED", null);
        send(completed("evt_late_" + course, course, "pi_late_" + course)).andExpect(status().isOk());
        // Le remboursement Stripe est tenté après commit (clé absente en test) : la course reste à rembourser.
        assertThat(statusOf(course)).isEqualTo("REFUND_PENDING");
    }

    @Test
    void unknownEventTypesAreAcknowledged() throws Exception {
        send(envelope("evt_other_" + UUID.randomUUID(), "customer.created", "{\"id\":\"cus_1\"}"))
                .andExpect(status().isOk()).andExpect(content().string("processed"));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions send(String json) throws Exception {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(payload, Instant.now().getEpochSecond());
        return mvc.perform(post("/api/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private static String completed(String eventId, UUID course, String paymentIntent) {
        return envelope(eventId, "checkout.session.completed", """
                {"id":"cs_test_%s","object":"checkout.session","client_reference_id":"%s",
                 "payment_status":"paid","payment_intent":"%s"}""".formatted(course, course, paymentIntent));
    }

    private static String expired(String eventId, UUID course) {
        return envelope(eventId, "checkout.session.expired", """
                {"id":"cs_test_%s","object":"checkout.session","client_reference_id":"%s","payment_status":"unpaid"}"""
                .formatted(course, course));
    }

    private static String envelope(String eventId, String type, String object) {
        return """
                {"id":"%s","object":"event","type":"%s","livemode":false,"data":{"object":%s}}"""
                .formatted(eventId, type, object);
    }

    private UUID insertCourse(String status, String paymentIntent) {
        return jdbc.sql("""
                insert into public.courses (client_id, item_description, pickup_address, pickup_lat, pickup_lng,
                    dropoff_address, dropoff_lat, dropoff_lng, weight_kg, distance_km, price_cents, commission_rate,
                    commission_cents, courier_amount_cents, status, stripe_payment_intent_id)
                values (?, 'Colis', '1 rue A, Lille', 50.63, 3.06, '2 rue B, Lille', 50.64, 3.07, 1, 1.2, 400, 0.20,
                    80, 320, ?::public.course_status, ?)
                returning id
                """)
                .params(clientId, status, paymentIntent)
                .query(UUID.class)
                .single();
    }

    private String handoverCode(UUID course) {
        return jdbc.sql("select code from public.course_handover_codes where course_id = ?").param(course).query(String.class).single();
    }

    private String statusOf(UUID course) {
        return jdbc.sql("select status::text from public.courses where id = ?").param(course).query(String.class).single();
    }

    private List<String> transitions(UUID course) {
        return jdbc.sql("""
                select coalesce(from_status::text, '') || '->' || to_status::text || ':' || actor
                from public.course_transitions where course_id = ? order by id
                """)
                .param(course)
                .query(String.class)
                .list();
    }
}
