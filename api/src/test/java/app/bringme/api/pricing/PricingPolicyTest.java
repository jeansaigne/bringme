package app.bringme.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.bringme.api.config.BringmeProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingPolicyTest {

    private static final List<BringmeProperties.WeightTier> TIERS = List.of(
            new BringmeProperties.WeightTier("S", "Petit", 2, 100),
            new BringmeProperties.WeightTier("M", "Moyen", 5, 300),
            new BringmeProperties.WeightTier("L", "Lourd", 10, 600),
            new BringmeProperties.WeightTier("XL", "Très lourd", 30, 1000));

    private static BringmeProperties.Pricing grid(String rate, List<BringmeProperties.WeightTier> tiers) {
        return new BringmeProperties.Pricing(300, 2, 50, new BigDecimal(rate), tiers);
    }

    private final PricingPolicy policy = new PricingPolicy(grid("0.20", TIERS));

    @ParameterizedTest(name = "{0} km, tranche {1} -> {2} cts")
    @CsvSource({
        "0,      S,  400",   // base + petit
        "2,      S,  400",   // 2 km pile : inclus
        "2.0004, S,  400",   // arrondi au mètre, toujours inclus
        "2.001,  S,  450",   // 1 m au-delà : 1 km commencé
        "4.4,    M,  750",   // 3 km commencés + moyen
        "3.38,   L,  1000",  // 2 km commencés + lourd
        "10,     XL, 1700",  // 8 km commencés + très lourd
    })
    void appliesTheGrid(String km, String tier, int expectedCents) {
        PriceBreakdown p = policy.price(new BigDecimal(km), tier);
        assertThat(p.priceCents()).isEqualTo(expectedCents);
        assertThat(p.weightTier()).isEqualTo(tier);
    }

    @Test
    void splitsCommissionAndCourierShare() {
        PriceBreakdown p = policy.price(new BigDecimal("4.4"), "M");
        assertThat(p.commissionCents()).isEqualTo(150);
        assertThat(p.courierAmountCents()).isEqualTo(600);
        assertThat(p.commissionRate()).isEqualByComparingTo("0.20");
    }

    @Test
    void roundsCommissionHalfUpToTheCent() {
        PriceBreakdown p = new PricingPolicy(grid("0.15", TIERS)).price(new BigDecimal("3"), "S");
        // 450 * 0.15 = 67.5 -> 68
        assertThat(p.commissionCents()).isEqualTo(68);
        assertThat(p.courierAmountCents()).isEqualTo(382);
    }

    @Test
    void shareInvariantHoldsAcrossTheGrid() {
        for (int km = 0; km <= 40; km++) {
            for (BringmeProperties.WeightTier t : TIERS) {
                PriceBreakdown p = policy.price(new BigDecimal(km).add(new BigDecimal("0.37")), t.code());
                assertThat(p.commissionCents() + p.courierAmountCents()).isEqualTo(p.priceCents());
                assertThat(p.courierAmountCents()).isPositive();
            }
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> policy.price(new BigDecimal("-0.1"), "S")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, "XXL")).isInstanceOf(UnknownWeightTierException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, null)).isInstanceOf(UnknownWeightTierException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new PricingPolicy(grid("1", TIERS))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", List.of()))).isInstanceOf(IllegalArgumentException.class);
        List<BringmeProperties.WeightTier> unordered = List.of(
                new BringmeProperties.WeightTier("M", "Moyen", 5, 300), new BringmeProperties.WeightTier("S", "Petit", 2, 100));
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", unordered))).isInstanceOf(IllegalArgumentException.class);
        List<BringmeProperties.WeightTier> duplicated = List.of(
                new BringmeProperties.WeightTier("S", "Petit", 2, 100), new BringmeProperties.WeightTier("S", "Autre", 5, 300));
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", duplicated))).isInstanceOf(IllegalArgumentException.class);
    }
}
