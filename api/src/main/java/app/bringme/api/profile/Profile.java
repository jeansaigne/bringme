package app.bringme.api.profile;

import app.bringme.api.pricing.Vehicle;
import java.util.UUID;

public record Profile(UUID id, String displayName, String stripeAccountId, Vehicle vehicle) {}
