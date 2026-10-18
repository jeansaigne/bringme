package app.bringme.api.geo;

public interface Geocoder {

    /** Résout une adresse postale française. Lève {@link AddressNotFoundException} si introuvable. */
    GeoPoint geocode(String address);
}
