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

    private static final List<BringmeProperties.SizeTier> TIERS = List.of(
            new BringmeProperties.SizeTier("S", "Petit", "sac à dos", 5, 50, Vehicle.FOOT_BIKE, 400, 80),
            new BringmeProperties.SizeTier("M", "Moyen", "coffre", 15, 150, Vehicle.CAR, 600, 100),
            new BringmeProperties.SizeTier("L", "Grand", "banquette rabattue", 30, 200, Vehicle.LARGE_CAR, 1200, 120));

    private static BringmeProperties.Pricing grid(String rate, List<BringmeProperties.SizeTier> tiers) {
        return new BringmeProperties.Pricing(2, new BigDecimal(rate), tiers);
    }

    private final PricingPolicy policy = new PricingPolicy(grid("0.20", TIERS));

    @ParameterizedTest(name = "{0} km, gabarit {1} -> {2} cts")
    @CsvSource({
        "0,      S, 400",    // forfait petit
        "2,      S, 400",    // 2 km pile : inclus
        "2.0004, S, 400",    // arrondi au mètre, toujours inclus
        "2.001,  S, 480",    // 1 m au-delà : 1 km commencé à 0,80 €
        "5.2,    S, 720",    // 4 km commencés
        "4.4,    M, 900",    // forfait moyen + 3 km à 1 €
        "10,     L, 2160",   // forfait grand + 8 km à 1,20 €
    })
    void appliesTheGrid(String km, String tier, int expectedCents) {
        PriceBreakdown p = policy.price(new BigDecimal(km), tier);
        assertThat(p.priceCents()).isEqualTo(expectedCents);
        assertThat(p.sizeTier()).isEqualTo(tier);
    }

    @Test
    void splitsCommissionAndCourierShare() {
        PriceBreakdown p = policy.price(new BigDecimal("4.4"), "M");
        assertThat(p.commissionCents()).isEqualTo(180);
        assertThat(p.courierAmountCents()).isEqualTo(720);
        assertThat(p.commissionRate()).isEqualByComparingTo("0.20");
    }

    @Test
    void roundsCommissionHalfUpToTheCent() {
        PricingPolicy policy1875 = new PricingPolicy(grid("0.1875", TIERS));
        // 400 * 0.1875 = 75 (exact)
        assertThat(policy1875.price(BigDecimal.ONE, "S").commissionCents()).isEqualTo(75);
        // 600 * 0.1875 = 112.5 -> 113 (demi-centime arrondi au supérieur)
        PriceBreakdown m = policy1875.price(BigDecimal.ONE, "M");
        assertThat(m.commissionCents()).isEqualTo(113);
        assertThat(m.courierAmountCents()).isEqualTo(487);
    }

    @Test
    void shareInvariantHoldsAcrossTheGrid() {
        for (int km = 0; km <= 40; km++) {
            for (BringmeProperties.SizeTier t : TIERS) {
                PriceBreakdown p = policy.price(new BigDecimal(km).add(new BigDecimal("0.37")), t.code());
                assertThat(p.commissionCents() + p.courierAmountCents()).isEqualTo(p.priceCents());
                assertThat(p.courierAmountCents()).isPositive();
            }
        }
    }

    @Test
    void vehiclesCarryOnlyCompatibleSizes() {
        assertThat(policy.tiersCarriedBy(Vehicle.FOOT_BIKE)).containsExactly("S");
        assertThat(policy.tiersCarriedBy(Vehicle.CAR)).containsExactly("S", "M");
        assertThat(policy.tiersCarriedBy(Vehicle.LARGE_CAR)).containsExactly("S", "M", "L");
        assertThat(policy.canCarry(Vehicle.FOOT_BIKE, "M")).isFalse();
        assertThat(policy.canCarry(Vehicle.CAR, "M")).isTrue();
        assertThat(policy.canCarry(null, "S")).isFalse();
        assertThat(policy.canCarry(Vehicle.LARGE_CAR, "XL")).isFalse();
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> policy.price(new BigDecimal("-0.1"), "S")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, "XL")).isInstanceOf(UnknownSizeTierException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, null)).isInstanceOf(UnknownSizeTierException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new PricingPolicy(grid("1", TIERS))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", List.of()))).isInstanceOf(IllegalArgumentException.class);
        List<BringmeProperties.SizeTier> duplicated = List.of(TIERS.get(0), TIERS.get(0));
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", duplicated))).isInstanceOf(IllegalArgumentException.class);
        List<BringmeProperties.SizeTier> noVehicle = List.of(new BringmeProperties.SizeTier("S", "Petit", "", 5, 50, null, 400, 80));
        assertThatThrownBy(() -> new PricingPolicy(grid("0.2", noVehicle))).isInstanceOf(IllegalArgumentException.class);
    }
}
