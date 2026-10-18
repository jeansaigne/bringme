package app.bringme.api.geo;

public class AddressNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AddressNotFoundException(String address) {
        super("Adresse introuvable : " + address);
    }
}
