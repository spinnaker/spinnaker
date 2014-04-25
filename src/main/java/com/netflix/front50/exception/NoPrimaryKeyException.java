package com.netflix.front50.exception;

/**
 * Created by aglover on 4/25/14.
 */
public class NoPrimaryKeyException extends RuntimeException {
    public NoPrimaryKeyException(String message) {
        super(message);
    }

    public NoPrimaryKeyException(Throwable cause) {
        super(cause);
    }
}
