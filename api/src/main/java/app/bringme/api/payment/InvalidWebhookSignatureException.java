package app.bringme.api.payment;

public class InvalidWebhookSignatureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
