package io.github.randomittin.voiceassistant;

/** Turns an {@link ApiException} into the sentence shown on the TV. Pure logic. */
public final class ErrorText {

    private ErrorText() {
    }

    /** {@code service} is what the user would recognise: "Claude", "ChatGPT" or "OpenAI speech-to-text". */
    public static String describe(String service, ApiException e) {
        switch (e.kind) {
            case AUTH:
                return service + " rejected the API key (" + e.httpStatus + "). Check it in settings.";
            case PAYMENT:
                return service + " says the account is out of credit or needs payment (402).";
            case RATE_LIMIT:
                return service + " is rate limiting or out of credit (429). Try again in a moment.";
            case SERVER:
                return service + " is having trouble (" + e.httpStatus + "). Try again.";
            case NETWORK:
                return "No connection to " + service + ". Check the TV's network.";
            case TIMEOUT:
                return service + " took too long to answer. Try again.";
            case BAD_REQUEST:
                String detail = e.getMessage() == null ? "" : e.getMessage();
                return service + " refused the request (" + e.httpStatus + ")"
                        + (detail.isEmpty() ? "." : ": " + detail);
            default:
                return e.getMessage();
        }
    }
}
