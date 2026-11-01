package app.bringme.api.geo;

import app.bringme.api.config.BringmeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Géocodage via la Géoplateforme IGN (Base Adresse Nationale), gratuit et sans clé.
 *
 * <p>Le front propose des adresses issues du même service ; l'API re-géocode toujours elle-même et n'accepte
 * qu'une adresse précise (type {@code housenumber}) avec un score suffisant. Le client ne peut donc ni envoyer
 * ses propres coordonnées, ni faire calculer un prix sur une rue ou une ville approximative.
 */
@Component
public class GeoplateformeGeocoder implements Geocoder {

    static final String PRECISE_TYPE = "housenumber";

    private final RestClient http;
    private final double minScore;

    public GeoplateformeGeocoder(BringmeProperties props, ClientHttpRequestFactory requestFactory) {
        this.http = RestClient.builder().requestFactory(requestFactory).baseUrl(props.geocoder().baseUrl()).build();
        this.minScore = props.geocoder().minScore();
    }

    @Override
    public GeoPoint geocode(String address) {
        JsonNode body;
        try {
            body = http.get()
                    .uri(b -> b.path("/search").queryParam("q", address).queryParam("limit", 1).build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Service de géocodage indisponible", e);
        }
        return pick(body, address, minScore);
    }

    /** Valide la meilleure réponse du géocodeur. Isolé pour être testé sans réseau. */
    static GeoPoint pick(JsonNode body, String address, double minScore) {
        JsonNode feature = body == null ? null : body.path("features").path(0);
        if (feature == null || feature.isMissingNode()) {
            throw AddressNotFoundException.notFound(address);
        }
        JsonNode props = feature.path("properties");
        if (!PRECISE_TYPE.equals(props.path("type").asText())) {
            throw AddressNotFoundException.imprecise(address);
        }
        if (props.path("score").asDouble(0) < minScore) {
            throw AddressNotFoundException.notFound(address);
        }
        JsonNode coords = feature.path("geometry").path("coordinates");
        return new GeoPoint(coords.path(1).asDouble(), coords.path(0).asDouble(), props.path("label").asText(address));
    }
}
