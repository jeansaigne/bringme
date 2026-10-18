package app.bringme.api.course;

public class IllegalTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CourseStatus from;
    private final CourseAction action;

    public IllegalTransitionException(CourseStatus from, CourseAction action) {
        super("Action " + action + " impossible depuis l'état " + from);
        this.from = from;
        this.action = action;
    }

    public CourseStatus from() {
        return from;
    }

    public CourseAction action() {
        return action;
    }
}
