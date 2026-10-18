package app.bringme.api.course;

import java.util.UUID;

/** La course a été modifiée entre la lecture et l'écriture (verrou optimiste). */
public class StaleCourseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StaleCourseException(UUID courseId) {
        super("Course modifiée de façon concurrente : " + courseId);
    }
}
