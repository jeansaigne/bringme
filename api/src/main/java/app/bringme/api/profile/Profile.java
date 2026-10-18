package app.bringme.api.profile;

import java.util.UUID;

public record Profile(UUID id, String displayName, String stripeAccountId) {}
