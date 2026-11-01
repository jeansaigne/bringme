package app.bringme.api.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeoplateformeGeocoderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode response(String type, double score) throws Exception {
        return mapper.readTree("""
                {"features":[{"geometry":{"coordinates":[3.0705,50.6366]},
                  "properties":{"label":"12 Rue Faidherbe 59800 Lille","type":"%s","score":%s}}]}
                """.formatted(type, score));
    }

    @Test
    void acceptsAPreciseAddress() throws Exception {
        GeoPoint p = GeoplateformeGeocoder.pick(response("housenumber", 0.97), "12 rue Faidherbe, Lille", 0.7);
        assertThat(p.label()).isEqualTo("12 Rue Faidherbe 59800 Lille");
        assertThat(p.lat()).isEqualTo(50.6366);
        assertThat(p.lng()).isEqualTo(3.0705);
    }

    @Test
    void rejectsAStreetOrACity() throws Exception {
        assertThatThrownBy(() -> GeoplateformeGeocoder.pick(response("street", 0.95), "rue Faidherbe", 0.7))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessageContaining("imprécise");
        assertThatThrownBy(() -> GeoplateformeGeocoder.pick(response("municipality", 0.99), "Lille", 0.7))
                .hasMessageContaining("imprécise");
    }

    @Test
    void rejectsALowConfidenceMatch() throws Exception {
        assertThatThrownBy(() -> GeoplateformeGeocoder.pick(response("housenumber", 0.42), "12 rue Fai", 0.7))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void rejectsAnEmptyAnswer() throws Exception {
        assertThatThrownBy(() -> GeoplateformeGeocoder.pick(mapper.readTree("{\"features\":[]}"), "nulle part", 0.7))
                .hasMessageContaining("introuvable");
        assertThatThrownBy(() -> GeoplateformeGeocoder.pick(null, "nulle part", 0.7)).hasMessageContaining("introuvable");
    }
}
