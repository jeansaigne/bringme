package app.bringme.api.config;

import app.bringme.api.pricing.Vehicle;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration applicative, alimentée par application.yml et les variables d'environnement. */
@ConfigurationProperties(prefix = "bringme")
public record BringmeProperties(
        String webUrl, Pricing pricing, Supabase supabase, Stripe stripe, Handover handover, Geocoder geocoder) {

    /** Grille tarifaire, en centimes : chaque gabarit a son propre forfait et son propre prix au km. */
    public record Pricing(int includedKm, BigDecimal commissionRate, List<SizeTier> sizeTiers) {}

    /**
     * Gabarit de l'objet, déterminé par le véhicule qu'il exige. Forfait jusqu'à {@code includedKm}, puis
     * {@code perKmCents} par km commencé au-delà.
     */
    public record SizeTier(
            String code, String label, String description, int maxKg, int maxLengthCm, Vehicle requiredVehicle,
            int baseCents, int perKmCents) {}

    public record Supabase(String url, String publishableKey) {}

    public record Stripe(
            String secretKey, String webhookSecret, String apiVersion, String apiBase, long webhookToleranceSeconds,
            List<String> checkoutPaymentMethods) {}

    public record Handover(int maxAttempts) {}

    public record Geocoder(String baseUrl, double minScore) {}
}
