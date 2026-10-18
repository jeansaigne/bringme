package app.bringme.api.payment.stripe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.bringme.api.payment.InvalidWebhookSignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final byte[] PAYLOAD = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

    private final StripeSignatureVerifier verifier =
            new StripeSignatureVerifier("whsec_test", 300, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsAValidSignature() {
        String header = verifier.sign(PAYLOAD, NOW.getEpochSecond());
        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
    }

    @Test
    void acceptsWhenOneOfSeveralSignaturesMatches() {
        String header = verifier.sign(PAYLOAD, NOW.getEpochSecond()) + ",v1=deadbeef,v0=ignored";
        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
    }

    @Test
    void rejectsATamperedPayload() {
        String header = verifier.sign(PAYLOAD, NOW.getEpochSecond());
        byte[] tampered = "{\"id\":\"evt_2\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(tampered, header)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsAnotherSecret() {
        String header = new StripeSignatureVerifier("whsec_other", 300, Clock.systemUTC()).sign(PAYLOAD, NOW.getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, header)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsReplayOutsideTolerance() {
        String header = verifier.sign(PAYLOAD, NOW.getEpochSecond() - 301);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, header)).hasMessageContaining("tolérance");
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, null)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "t=abc,v1=00")).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "v1=00")).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void refusesToVerifyWithoutASecret() {
        StripeSignatureVerifier unconfigured = new StripeSignatureVerifier("", 300, Clock.systemUTC());
        assertThatThrownBy(() -> unconfigured.verify(PAYLOAD, "t=1,v1=00")).hasMessageContaining("non configuré");
    }
}
