package app.bringme.api.course;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Journal des événements de paiement déjà traités (idempotence des webhooks). */
@Repository
public class StripeEventRepository {

    private final JdbcClient jdbc;

    public StripeEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} si l'événement est nouveau, {@code false} s'il a déjà été enregistré. */
    public boolean tryRecord(String eventId, String type) {
        return jdbc.sql("insert into public.stripe_events (event_id, type) values (?, ?) on conflict (event_id) do nothing")
                .params(eventId, type)
                .update() == 1;
    }
}
