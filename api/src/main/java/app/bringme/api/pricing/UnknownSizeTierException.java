package app.bringme.api.pricing;

public class UnknownSizeTierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownSizeTierException(String code) {
        super("Gabarit inconnu : " + code);
    }
}
