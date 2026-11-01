package app.bringme.api.pricing;

/**
 * Moyen de transport déclaré par le coursier, ordonné par capacité : un véhicule transporte tous les gabarits
 * dont le véhicule requis a une capacité inférieure ou égale.
 */
public enum Vehicle {
    FOOT_BIKE(1),
    CAR(2),
    LARGE_CAR(3);

    private final int capacity;

    Vehicle(int capacity) {
        this.capacity = capacity;
    }

    public boolean canCarry(Vehicle required) {
        return capacity >= required.capacity;
    }
}
