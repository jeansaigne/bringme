package app.bringme.api.web;

import app.bringme.api.config.ApiException;
import app.bringme.api.payment.PaymentProvider;
import app.bringme.api.payment.PaymentProviderException;
import app.bringme.api.profile.Profile;
import app.bringme.api.profile.ProfileRepository;
import app.bringme.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CourierController {

    public record Me(String id, String email, String displayName, Courier courier) {}

    public record Courier(String accountId, boolean transfersActive, String transfersStatus) {}

    public record AccountResponse(String accountId) {}

    public record SessionResponse(String clientSecret) {}

    public record LinkResponse(String url) {}

    private final ProfileRepository profiles;
    private final PaymentProvider payments;

    public CourierController(ProfileRepository profiles, PaymentProvider payments) {
        this.profiles = profiles;
        this.payments = payments;
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        Profile p = profiles.getOrCreate(user.id(), user.email());
        Courier courier = null;
        if (p.stripeAccountId() != null) {
            try {
                PaymentProvider.PayeeStatus s = payments.payeeStatus(p.stripeAccountId());
                courier = new Courier(p.stripeAccountId(), s.transfersActive(), s.transfersStatus());
            } catch (PaymentProviderException e) {
                courier = new Courier(p.stripeAccountId(), false, "unknown");
            }
        }
        return new Me(user.id().toString(), user.email(), p.displayName(), courier);
    }

    @PostMapping("/courier/account")
    public AccountResponse createAccount(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        Profile p = profiles.getOrCreate(user.id(), user.email());
        if (p.stripeAccountId() != null) {
            return new AccountResponse(p.stripeAccountId());
        }
        String accountId = payments.createPayeeAccount(user.id(), user.email(), p.displayName());
        if (!profiles.attachStripeAccount(user.id(), accountId)) {
            accountId = profiles.getOrCreate(user.id(), user.email()).stripeAccountId();
        }
        return new AccountResponse(accountId);
    }

    @PostMapping("/courier/account-session")
    public SessionResponse accountSession(@AuthenticationPrincipal Jwt jwt) {
        return new SessionResponse(payments.createOnboardingSession(requireAccount(jwt)));
    }

    @PostMapping("/courier/login-link")
    public LinkResponse loginLink(@AuthenticationPrincipal Jwt jwt) {
        return new LinkResponse(payments.createPayeeDashboardLink(requireAccount(jwt)));
    }

    private String requireAccount(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        Profile p = profiles.getOrCreate(user.id(), user.email());
        if (p.stripeAccountId() == null) {
            throw ApiException.conflict("payee_missing", "Créez d'abord votre compte coursier");
        }
        return p.stripeAccountId();
    }
}
