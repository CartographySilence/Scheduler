package smtsched.spec;

public class SpecException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SpecException(String message) { super (message); }
    public SpecException(String message, Throwable cause) { super(message, cause); }
}