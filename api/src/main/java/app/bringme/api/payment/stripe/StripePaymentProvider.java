package app.bringme.api.payment.stripe;

import app.bringme.api.config.BringmeProperties;
import app.bringme.api.payment.PaymentEvent;
import app.bringme.api.payment.PaymentProvider;
import app.bringme.api.payment.PaymentProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implémentation Stripe, en HTTP direct (API v1 en form-encoded, API v2 en JSON).
 *
 * <ul>
 *   <li>Encaissement : Stripe Checkout sur le compte plateforme (charges et transferts séparés).
 *   <li>Coursiers : comptes Accounts v2, configuration {@code recipient}, capacité {@code stripe_transfers}.
 *   <li>Toutes les écritures portent une clé d'idempotence dérivée de l'identifiant métier.
 * </ul>
 */
@Component
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final RestClient http;
    private final String secretKey;
    private final StripeSignatureVerifier signatureVerifier;
    private final ObjectMapper mapper;

    public StripePaymentProvider(BringmeProperties props, ClientHttpRequestFactory requestFactory, ObjectMapper mapper, Clock clock) {
        BringmeProperties.Stripe cfg = props.stripe();
        this.secretKey = cfg.secretKey();
        this.mapper = mapper;
        this.signatureVerifier = new StripeSignatureVerifier(cfg.webhookSecret(), cfg.webhookToleranceSeconds(), clock);
        this.http = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(cfg.apiBase())
                .defaultHeader("Stripe-Version", cfg.apiVersion())
                .build();
    }

    // ------------------------------------------------------------------ encaissement

    @Override
    public CheckoutSession createCheckout(CheckoutRequest r) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("locale", "fr");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", r.currency());
        form.add("line_items[0][price_data][unit_amount]", Integer.toString(r.amountCents()));
        form.add("line_items[0][price_data][product_data][name]", r.description());
        form.add("success_url", r.successUrl());
        form.add("cancel_url", r.cancelUrl());
        form.add("client_reference_id", r.courseId().toString());
        form.add("expires_at", Long.toString(r.expiresAt().getEpochSecond()));
        form.add("metadata[course_id]", r.courseId().toString());
        form.add("payment_intent_data[metadata][course_id]", r.courseId().toString());
        form.add("payment_intent_data[transfer_group]", transferGroup(r.courseId()));
        if (r.customerEmail() != null) {
            form.add("customer_email", r.customerEmail());
        }
        JsonNode session = postForm("/v1/checkout/sessions", form, "checkout-" + r.courseId());
        return new CheckoutSession(session.path("id").asText(), session.path("url").asText());
    }

    @Override
    public boolean expireCheckout(String sessionId) {
        try {
            postForm("/v1/checkout/sessions/" + sessionId + "/expire", new LinkedMultiValueMap<>(), null);
            return true;
        } catch (PaymentProviderException e) {
            LOG.info("Session {} non expirable : {}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public RefundResult refund(UUID courseId, String paymentIntentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntentId);
        form.add("reason", "requested_by_customer");
        form.add("metadata[course_id]", courseId.toString());
        JsonNode refund = postForm("/v1/refunds", form, "refund-" + courseId);
        return new RefundResult(refund.path("id").asText(), "succeeded".equals(refund.path("status").asText()));
    }

    // ------------------------------------------------------------------ coursiers (Connect, Accounts v2)

    @Override
    public String createPayeeAccount(UUID userId, String email, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contact_email", email);
        body.put("display_name", displayName);
        body.put("dashboard", "express");
        body.put("identity", Map.of("country", "FR", "entity_type", "individual"));
        body.put("configuration", Map.of("recipient", Map.of("capabilities",
                Map.of("stripe_balance", Map.of("stripe_transfers", Map.of("requested", true))))));
        body.put("defaults", Map.of(
                "currency", "eur",
                "locales", List.of("fr-FR"),
                "responsibilities", Map.of("fees_collector", "application", "losses_collector", "application")));
        body.put("metadata", Map.of("user_id", userId.toString()));
        JsonNode account = call(() -> http.post()
                .uri("/v2/core/accounts")
                .headers(h -> {
                    h.setBearerAuth(requireKey());
                    h.set("Idempotency-Key", "account-" + userId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return account.path("id").asText();
    }

    @Override
    public String createOnboardingSession(String accountId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("account", accountId);
        form.add("components[account_onboarding][enabled]", "true");
        form.add("components[account_onboarding][features][external_account_collection]", "true");
        return postForm("/v1/account_sessions", form, null).path("client_secret").asText();
    }

    @Override
    public String createPayeeDashboardLink(String accountId) {
        return postForm("/v1/accounts/" + accountId + "/login_links", new LinkedMultiValueMap<>(), null).path("url").asText();
    }

    @Override
    public PayeeStatus payeeStatus(String accountId) {
        JsonNode account = call(() -> http.get()
                .uri("/v2/core/accounts/{id}?include=configuration.recipient", accountId)
                .headers(h -> h.setBearerAuth(requireKey()))
                .retrieve()
                .body(JsonNode.class));
        String status = account.path("configuration").path("recipient").path("capabilities")
                .path("stripe_balance").path("stripe_transfers").path("status").asText("unrequested");
        return new PayeeStatus("active".equals(status), status);
    }

    @Override
    public TransferResult transfer(TransferRequest r) {
        JsonNode intent = call(() -> http.get()
                .uri("/v1/payment_intents/{id}", r.paymentReference())
                .headers(h -> h.setBearerAuth(requireKey()))
                .retrieve()
                .body(JsonNode.class));
        String chargeId = intent.path("latest_charge").asText(null);
        if (chargeId == null || !"succeeded".equals(intent.path("status").asText())) {
            throw new PaymentProviderException("Paiement client non encaissé, transfert impossible");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Integer.toString(r.amountCents()));
        form.add("currency", r.currency());
        form.add("destination", r.payeeAccountId());
        form.add("source_transaction", chargeId);
        form.add("transfer_group", transferGroup(r.courseId()));
        form.add("metadata[course_id]", r.courseId().toString());
        JsonNode transfer = postForm("/v1/transfers", form, "transfer-" + r.courseId());
        return new TransferResult(transfer.path("id").asText(), chargeId);
    }

    // ------------------------------------------------------------------ webhooks

    @Override
    public PaymentEvent parseWebhook(byte[] payload, String signatureHeader) {
        signatureVerifier.verify(payload, signatureHeader);
        JsonNode event;
        try {
            event = mapper.readTree(payload);
        } catch (java.io.IOException e) {
            throw new PaymentProviderException("Webhook illisible", e);
        }
        String id = event.path("id").asText();
        String type = event.path("type").asText();
        JsonNode object = event.path("data").path("object");
        return switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                UUID courseId = parseUuid(object.path("client_reference_id").asText(null));
                String status = object.path("payment_status").asText();
                yield courseId == null
                        ? new PaymentEvent.Ignored(id, type)
                        : new PaymentEvent.CheckoutCompleted(id, type, courseId, object.path("payment_intent").asText(null),
                                "paid".equals(status) || "no_payment_required".equals(status));
            }
            case "checkout.session.expired" -> {
                UUID courseId = parseUuid(object.path("client_reference_id").asText(null));
                yield courseId == null ? new PaymentEvent.Ignored(id, type) : new PaymentEvent.CheckoutExpired(id, type, courseId);
            }
            case "charge.refunded" -> new PaymentEvent.PaymentRefunded(
                    id, type, object.path("payment_intent").asText(null), object.path("refunded").asBoolean(false));
            default -> new PaymentEvent.Ignored(id, type);
        };
    }

    // ------------------------------------------------------------------ plomberie HTTP

    private JsonNode postForm(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        return call(() -> http.post()
                .uri(path)
                .headers(h -> {
                    h.setBearerAuth(requireKey());
                    if (idempotencyKey != null) {
                        h.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode call(Supplier<JsonNode> request) {
        try {
            JsonNode body = request.get();
            if (body == null) {
                throw new PaymentProviderException("Réponse Stripe vide");
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new PaymentProviderException("Stripe " + e.getStatusCode().value() + " : " + errorMessage(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new PaymentProviderException("Stripe injoignable", e);
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode error = mapper.readTree(body).path("error");
            return error.path("message").asText(error.path("code").asText("erreur inconnue"));
        } catch (JsonProcessingException e) {
            return "réponse illisible";
        }
    }

    private String requireKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentProviderException("Paiement non configuré (STRIPE_SECRET_KEY manquante)");
        }
        return secretKey;
    }

    private static String transferGroup(UUID courseId) {
        return "course_" + courseId;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
