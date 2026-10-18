package app.bringme.api.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Utilisateur authentifié, extrait du JWT Supabase. */
public record CurrentUser(UUID id, String email) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
    }
}
