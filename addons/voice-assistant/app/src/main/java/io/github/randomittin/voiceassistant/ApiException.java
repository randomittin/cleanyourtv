package io.github.randomittin.voiceassistant;

/** A failed call to an API, classified so the screen can say something useful. */
public final class ApiException extends Exception {

    public enum Kind { AUTH, PAYMENT, RATE_LIMIT, BAD_REQUEST, SERVER, NETWORK, TIMEOUT, EMPTY, REFUSED }

    public final Kind kind;
    public final int httpStatus;

    public ApiException(Kind kind, int httpStatus, String message) {
        super(message);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public static Kind kindForStatus(int status) {
        if (status == 401 || status == 403) {
            return Kind.AUTH;
        }
        if (status == 402) {
            return Kind.PAYMENT;
        }
        if (status == 429) {
            return Kind.RATE_LIMIT;
        }
        if (status >= 500) {
            return Kind.SERVER;
        }
        return Kind.BAD_REQUEST;
    }
}
