package app.bringme.api.geo;

public record GeoPoint(double lat, double lng, String label) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Distance orthodromique (vol d'oiseau), formule de haversine. */
    public double distanceKmTo(GeoPoint other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLng = Math.toRadians(other.lng - lng);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
