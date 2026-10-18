package app.bringme.api.security;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Valide les JWT émis par Supabase Auth.
 *
 * <ul>
 *   <li>Clés asymétriques (ES256/RS256) : vérification locale de la signature via le JWKS du projet.
 *   <li>Secret partagé legacy (HS256) : l'API ne détient pas le secret ; le jeton est validé auprès de
 *       Supabase Auth ({@code GET /auth/v1/user}) puis mis en cache 60 s au maximum.
 * </ul>
 * Dans les deux cas, l'émetteur, l'expiration et l'audience {@code authenticated} sont contrôlés.
 */
public class SupabaseJwtDecoder implements JwtDecoder {

    private static final long CACHE_SECONDS = 60;

    private final JwtDecoder asymmetricDecoder;
    private final OAuth2TokenValidator<Jwt> validator;
    private final RestClient http;
    private final String issuer;
    private final String publishableKey;
    private final Map<String, Instant> verifiedTokens = new ConcurrentHashMap<>();

    public SupabaseJwtDecoder(String supabaseUrl, String publishableKey, RestClient http) {
        this.issuer = supabaseUrl + "/auth/v1";
        this.publishableKey = publishableKey;
        this.http = http;
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains("authenticated"));
        this.validator = new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience);
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        nimbus.setJwtValidator(validator);
        this.asymmetricDecoder = nimbus;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JWT parsed;
        try {
            parsed = JWTParser.parse(token);
        } catch (ParseException e) {
            throw new BadJwtException("Jeton mal formé", e);
        }
        String alg = parsed.getHeader().getAlgorithm().getName();
        if (!"HS256".equals(alg)) {
            return asymmetricDecoder.decode(token);
        }
        return decodeViaAuthServer(token, parsed);
    }

    private Jwt decodeViaAuthServer(String token, JWT parsed) {
        Jwt jwt = toJwt(token, parsed);
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        if (result.hasErrors()) {
            throw new JwtValidationException("Jeton invalide", result.getErrors());
        }
        Instant now = Instant.now();
        Instant cachedUntil = verifiedTokens.get(token);
        if (cachedUntil == null || cachedUntil.isBefore(now)) {
            try {
                http.get()
                        .uri(issuer + "/user")
                        .header("apikey", publishableKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                throw new BadJwtException("Jeton refusé par Supabase Auth", e);
            }
            if (verifiedTokens.size() > 5_000) {
                verifiedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
            }
            Instant until = now.plusSeconds(CACHE_SECONDS);
            if (jwt.getExpiresAt() != null && jwt.getExpiresAt().isBefore(until)) {
                until = jwt.getExpiresAt();
            }
            verifiedTokens.put(token, until);
        }
        return jwt;
    }

    private static Jwt toJwt(String token, JWT parsed) {
        JWTClaimsSet claims;
        try {
            claims = parsed.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new BadJwtException("Claims illisibles", e);
        }
        Map<String, Object> values = new HashMap<>();
        values.put(JwtClaimNames.SUB, claims.getSubject());
        values.put(JwtClaimNames.ISS, claims.getIssuer());
        values.put(JwtClaimNames.AUD, claims.getAudience());
        if (claims.getClaim("email") != null) {
            values.put("email", claims.getClaim("email"));
        }
        if (claims.getClaim("role") != null) {
            values.put("role", claims.getClaim("role"));
        }
        if (claims.getIssueTime() != null) {
            values.put(JwtClaimNames.IAT, claims.getIssueTime().toInstant());
        }
        if (claims.getExpirationTime() == null) {
            throw new BadJwtException("Jeton sans expiration");
        }
        values.put(JwtClaimNames.EXP, claims.getExpirationTime().toInstant());
        values.values().removeIf(v -> v == null);
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .claims(c -> c.putAll(values))
                .build();
    }
}
