package app.bringme.api.payment.stripe;

import app.bringme.api.payment.InvalidWebhookSignatureException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vérification de l'en-tête {@code Stripe-Signature} : HMAC-SHA256 de {@code "<t>.<payload>"} avec le secret
 * de l'endpoint, comparaison à temps constant, et fenêtre de tolérance contre le rejeu.
 */
public final class StripeSignatureVerifier {

    private final String secret;
    private final long toleranceSeconds;
    private final Clock clock;

    public StripeSignatureVerifier(String secret, long toleranceSeconds, Clock clock) {
        this.secret = secret;
        this.toleranceSeconds = toleranceSeconds;
        this.clock = clock;
    }

    public void verify(byte[] payload, String header) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidWebhookSignatureException("Secret de webhook non configuré");
        }
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookSignatureException("En-tête Stripe-Signature absent");
        }
        long timestamp = -1;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    throw new InvalidWebhookSignatureException("Horodatage invalide");
                }
            } else if ("v1".equals(kv[0])) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp < 0 || signatures.isEmpty()) {
            throw new InvalidWebhookSignatureException("En-tête Stripe-Signature incomplet");
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > toleranceSeconds) {
            throw new InvalidWebhookSignatureException("Horodatage hors tolérance");
        }
        byte[] expected = HexFormat.of().formatHex(hmac(timestamp, payload)).getBytes(StandardCharsets.US_ASCII);
        for (String candidate : signatures) {
            if (MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.US_ASCII))) {
                return;
            }
        }
        throw new InvalidWebhookSignatureException("Signature invalide");
    }

    /** Signe un payload comme le fait Stripe (utile aux tests). */
    public String sign(byte[] payload, long timestamp) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac(timestamp, payload));
    }

    private byte[] hmac(long timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
