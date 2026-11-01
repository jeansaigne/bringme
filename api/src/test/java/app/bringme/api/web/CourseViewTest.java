package app.bringme.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.bringme.api.course.Course;
import app.bringme.api.course.CourseStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseViewTest {

    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static Course course(CourseStatus status, UUID courier) {
        return new Course(UUID.randomUUID(), CLIENT, courier, "Colis", "Digicode 4521B", "A", 50.6, 3.0, "B", 50.7, 3.1, "S",
                new BigDecimal("1.2"), 400, new BigDecimal("0.20"), 80, 320, "eur", status, 0, null, null, null, null, null, null, 0,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null, null, null, null);
    }

    @Test
    void instructionsAreHiddenFromCandidateCouriers() {
        CourseView view = CourseView.of(course(CourseStatus.PAID, null), STRANGER, null);
        assertThat(view.role()).isEqualTo("candidate");
        assertThat(view.instructions()).isNull();
        assertThat(view.actions()).containsExactly("accept");
    }

    @Test
    void instructionsAreVisibleToTheirAuthor() {
        assertThat(CourseView.of(course(CourseStatus.PAID, null), CLIENT, "123456").instructions()).isEqualTo("Digicode 4521B");
    }

    @Test
    void instructionsAreVisibleToTheCourierOnceAccepted() {
        CourseView view = CourseView.of(course(CourseStatus.ACCEPTED, COURIER), COURIER, null);
        assertThat(view.role()).isEqualTo("courier");
        assertThat(view.instructions()).isEqualTo("Digicode 4521B");
    }

    @Test
    void anotherCourierNeverSeesThemAfterAcceptance() {
        assertThat(CourseView.of(course(CourseStatus.ACCEPTED, COURIER), STRANGER, null).instructions()).isNull();
    }
}
