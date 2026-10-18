package app.bringme.api.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void computesGreatCircleDistance() {
        GeoPoint lille = new GeoPoint(50.6365, 3.0635, "Lille");
        GeoPoint paris = new GeoPoint(48.8566, 2.3522, "Paris");
        assertThat(lille.distanceKmTo(paris)).isCloseTo(204.0, within(2.0));
        assertThat(lille.distanceKmTo(lille)).isZero();
    }
}
