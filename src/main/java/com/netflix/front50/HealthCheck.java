package com.netflix.front50;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by aglover on 5/9/14.
 */
@Component
public class HealthCheck implements HealthIndicator<String> {

    @Autowired
    SimpleDBDAO dao;

    @Override
    public String health() {
        try {
            if (!this.dao.isHealthly()) {
                throw new NotHealthlyException();
            }
            return "Ok";
        } catch (Throwable thr) {
            throw new NotHealthlyException();
        }
    }

    @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Not Healthy!")
    public class NotHealthlyException extends RuntimeException {
        public NotHealthlyException() {
            super();
        }
    }
}
