package app.bringme.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.bringme.api.config.BringmeProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingPolicyTest {

    private static BringmeProperties.Pricing grid(String rate) {
        return new BringmeProperties.Pricing(300, 2, 50, 100, new BigDecimal(rate), new BigDecimal("30"));
    }

    private final PricingPolicy policy = new PricingPolicy(grid("0.20"));

    @ParameterizedTest(name = "{0} km, {1} kg -> {2} cts")
    @CsvSource({
        "0,      1,     400",  // base + 1 kg
        "1.5,    0.2,   400",  // kg commencé
        "2,      1,     400",  // 2 km pile : inclus
        "2.0004, 1,     400",  // arrondi au mètre, toujours inclus
        "2.001,  1,     450",  // 1 m au-delà : 1 km commencé
        "3,      1,     450",
        "5.2,    3.1,   900",  // 4 km commencés + 4 kg commencés
        "10,     30,    3700", // poids maximal
    })
    void appliesTheGrid(String km, String kg, int expectedCents) {
        PriceBreakdown p = policy.price(new BigDecimal(km), new BigDecimal(kg));
        assertThat(p.priceCents()).isEqualTo(expectedCents);
    }

    @Test
    void splitsCommissionAndCourierShare() {
        PriceBreakdown p = policy.price(new BigDecimal("5.2"), new BigDecimal("3.1"));
        assertThat(p.commissionCents()).isEqualTo(180);
        assertThat(p.courierAmountCents()).isEqualTo(720);
        assertThat(p.commissionRate()).isEqualByComparingTo("0.20");
    }

    @Test
    void roundsCommissionHalfUpToTheCent() {
        PriceBreakdown p = new PricingPolicy(grid("0.15")).price(new BigDecimal("3"), BigDecimal.ONE);
        // 450 * 0.15 = 67.5 -> 68
        assertThat(p.commissionCents()).isEqualTo(68);
        assertThat(p.courierAmountCents()).isEqualTo(382);
    }

    @Test
    void shareInvariantHoldsAcrossTheGrid() {
        for (int km = 0; km <= 40; km++) {
            for (int kg = 1; kg <= 30; kg++) {
                PriceBreakdown p = policy.price(new BigDecimal(km).add(new BigDecimal("0.37")), new BigDecimal(kg).subtract(new BigDecimal("0.5")));
                assertThat(p.commissionCents() + p.courierAmountCents()).isEqualTo(p.priceCents());
                assertThat(p.courierAmountCents()).isPositive();
            }
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> policy.price(new BigDecimal("-0.1"), BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.price(BigDecimal.ONE, new BigDecimal("30.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingPolicy(grid("1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
