package app.bringme.api.web;

import app.bringme.api.course.Course;
import app.bringme.api.course.CourseService;
import app.bringme.api.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CourseController {

    public record QuoteRequest(
            @NotBlank @Size(min = 3, max = 300) String pickupAddress,
            @NotBlank @Size(min = 3, max = 300) String dropoffAddress,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "30") BigDecimal weightKg) {}

    public record CreateCourseRequest(
            @NotBlank @Size(max = 500) String itemDescription,
            @NotBlank @Size(min = 3, max = 300) String pickupAddress,
            @NotBlank @Size(min = 3, max = 300) String dropoffAddress,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "30") BigDecimal weightKg) {}

    public record QuoteResponse(
            String pickupAddress, String dropoffAddress, BigDecimal distanceKm, BigDecimal weightKg, int priceCents,
            BigDecimal commissionRate, int commissionCents, int courierAmountCents) {}

    public record CreatedResponse(CourseView course, String checkoutUrl) {}

    public record DeliverRequest(@NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*") String code) {}

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @PostMapping("/quotes")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest req) {
        CourseService.Quote q = service.quote(req.pickupAddress(), req.dropoffAddress(), req.weightKg());
        return new QuoteResponse(q.pickup().label(), q.dropoff().label(), q.price().distanceKm(), q.price().weightKg(),
                q.price().priceCents(), q.price().commissionRate(), q.price().commissionCents(), q.price().courierAmountCents());
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCourseRequest req) {
        CurrentUser user = CurrentUser.from(jwt);
        CourseService.CreatedCourse created = service.create(
                user, req.itemDescription().trim(), req.pickupAddress(), req.dropoffAddress(), req.weightKg());
        return new CreatedResponse(CourseView.of(created.course(), user.id(), null), created.checkoutUrl());
    }

    @GetMapping("/courses/mine")
    public List<CourseView> mine(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return service.mine(user).stream().map(c -> view(user, c)).toList();
    }

    @GetMapping("/courses/available")
    public List<CourseView> available(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return service.available(user).stream().map(c -> CourseView.of(c, user.id(), null)).toList();
    }

    @GetMapping("/courses/{id}")
    public CourseView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CurrentUser user = CurrentUser.from(jwt);
        return view(user, service.get(user, id));
    }

    @PostMapping("/courses/{id}/cancel")
    public CourseView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CurrentUser user = CurrentUser.from(jwt);
        return view(user, service.cancel(user, id));
    }

    @PostMapping("/courses/{id}/accept")
    public CourseView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CurrentUser user = CurrentUser.from(jwt);
        return view(user, service.accept(user, id));
    }

    @PostMapping("/courses/{id}/deliver")
    public CourseView deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody DeliverRequest req) {
        CurrentUser user = CurrentUser.from(jwt);
        return view(user, service.deliver(user, id, req.code()));
    }

    @PostMapping("/courses/{id}/retry-payout")
    public CourseView retryPayout(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CurrentUser user = CurrentUser.from(jwt);
        return view(user, service.retryPayout(user, id));
    }

    private CourseView view(CurrentUser user, Course c) {
        return CourseView.of(c, user.id(), service.handoverCodeFor(user, c));
    }
}
