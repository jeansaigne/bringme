package app.bringme.api.pricing;

public class UnknownWeightTierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownWeightTierException(String code) {
        super("Tranche de poids inconnue : " + code);
    }
}
