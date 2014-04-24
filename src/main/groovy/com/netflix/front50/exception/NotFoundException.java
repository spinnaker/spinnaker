package com.netflix.front50.exception;

/**
 * Created by aglover on 4/23/14.
 */
public class NotFoundException extends Exception {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Throwable cause) {
        super(cause);
    }
}
