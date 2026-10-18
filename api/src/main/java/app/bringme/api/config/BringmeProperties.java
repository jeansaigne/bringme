package app.bringme.api.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration applicative, alimentée par application.yml et les variables d'environnement. */
@ConfigurationProperties(prefix = "bringme")
public record BringmeProperties(
        String webUrl, Pricing pricing, Supabase supabase, Stripe stripe, Handover handover, Geocoder geocoder) {

    /** Grille tarifaire, en centimes. */
    public record Pricing(
            int baseCents, int includedKm, int perKmCents, int perKgCents, BigDecimal commissionRate, BigDecimal maxWeightKg) {}

    public record Supabase(String url, String publishableKey) {}

    public record Stripe(String secretKey, String webhookSecret, String apiVersion, String apiBase, long webhookToleranceSeconds) {}

    public record Handover(int maxAttempts) {}

    public record Geocoder(String baseUrl, double minScore) {}
}
