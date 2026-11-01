package app.bringme.api.geo;

public class AddressNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AddressNotFoundException(String message) {
        super(message);
    }

    public static AddressNotFoundException notFound(String address) {
        return new AddressNotFoundException("Adresse introuvable : « " + address + " ». Choisissez une adresse dans la liste proposée.");
    }

    public static AddressNotFoundException imprecise(String address) {
        return new AddressNotFoundException(
                "Adresse imprécise : « " + address + " ». Il faut une adresse complète avec numéro, choisie dans la liste proposée.");
    }
}
