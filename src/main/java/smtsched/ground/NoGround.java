package smtsched.ground;

public class NoGround implements Grounder {
    @Override public String ground(String smtlib) { return smtlib; }
}