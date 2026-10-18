package app.bringme.api.geo;

import app.bringme.api.config.BringmeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Géocodage via le service gratuit de la Géoplateforme IGN (Base Adresse Nationale), sans clé. */
@Component
public class GeoplateformeGeocoder implements Geocoder {

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
        JsonNode feature = body == null ? null : body.path("features").path(0);
        if (feature == null || feature.isMissingNode() || feature.path("properties").path("score").asDouble(0) < minScore) {
            throw new AddressNotFoundException(address);
        }
        JsonNode coords = feature.path("geometry").path("coordinates");
        return new GeoPoint(coords.path(1).asDouble(), coords.path(0).asDouble(), feature.path("properties").path("label").asText(address));
    }
}
