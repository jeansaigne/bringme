package app.bringme.api.course;

import app.bringme.api.geo.GeoPoint;
import app.bringme.api.pricing.PriceBreakdown;
import java.util.UUID;

public record NewCourse(UUID clientId, String itemDescription, GeoPoint pickup, GeoPoint dropoff, PriceBreakdown price) {}
