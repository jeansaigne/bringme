package app.bringme.api.profile;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {

    private final JdbcClient jdbc;

    public ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Le trigger d'inscription crée normalement le profil ; ceci couvre les comptes créés avant lui. */
    public Profile getOrCreate(UUID id, String email) {
        String displayName = email == null ? null : email.split("@", 2)[0];
        jdbc.sql("insert into public.profiles (id, display_name) values (?, left(?, 80)) on conflict (id) do nothing")
                .params(id, displayName)
                .update();
        return jdbc.sql("select id, display_name, stripe_account_id from public.profiles where id = ?")
                .param(id)
                .query((rs, n) -> new Profile(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("stripe_account_id")))
                .single();
    }

    /** N'écrase jamais un compte existant (création idempotente). */
    public boolean attachStripeAccount(UUID id, String accountId) {
        return jdbc.sql("update public.profiles set stripe_account_id = ? where id = ? and stripe_account_id is null")
                .params(accountId, id)
                .update() == 1;
    }
}
