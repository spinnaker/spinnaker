package com.netflix.front50;

import com.netflix.front50.exception.NoPrimaryKeyException;
import com.netflix.front50.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;

@RestController
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.netflix.front50")
public class Front50Controller {

    static final Logger LOG = LoggerFactory.getLogger(Front50Controller.class);

    public static void main(String[] args) {
        SpringApplication.run(Front50Controller.class, args);
    }

    @Autowired
    Application application;

    @RequestMapping(method = RequestMethod.PUT)
    @ResponseBody
    public Application put(@RequestBody Application app) {
        try {
            if (app.getName() == null || app.getName().equals("")) {
                throw new ApplicationWithoutNameException("Application must have a name");
            }
            Application foundApp = application.findByName(app.getName());
            application.initialize(foundApp).withName(app.getName()).update(app.allSetColumnProperties());
            return application;
        } catch (NotFoundException e) {
            LOG.error("PUT::App not found: " + app.getName(), e);
            throw new ApplicationNotFoundException(e);
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public Application post(@RequestBody Application app) {
        try {
            return application.initialize(app).withName(app.getName()).save();
        } catch (NoPrimaryKeyException e) {
            LOG.error("POST::App not found: " + app.getName(), e);
            throw new ApplicationWithoutNameException(e);
        } catch (Throwable thr) {
            LOG.error("POST:: throwable occurred: " + app.getName(), thr);
            throw new ApplicationException(thr);
        }
    }

    @RequestMapping(method = RequestMethod.GET)
    public Collection<Application> index() {
        try {
            return application.findAll();
        } catch (NotFoundException e) {
            LOG.error("GET(/) -> NotFoundException occurred: ", e);
            throw new NoApplicationsFoundException(e);
        } catch (Throwable thr) {
            LOG.error("GET(/) -> Throwable occurred: ", thr);
            throw new ApplicationException(thr);
        }
    }

    @RequestMapping(value = "/name/{name}", method = RequestMethod.GET)
    public Application getByName(@PathVariable String name) {
        try {
            return application.findByName(name);
        } catch (NotFoundException e) {
            LOG.error("GET(/name/{name}) -> NotFoundException occurred for app: " + name, e);
            throw new ApplicationNotFoundException(e);
        } catch (Throwable thr) {
            LOG.error("GET(/name/{name}) -> Throwable occurred: ", thr);
            throw new ApplicationException(thr);
        }
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Applications must have a name")
    class ApplicationWithoutNameException extends RuntimeException {
        public ApplicationWithoutNameException(Throwable cause) {
            super(cause);
        }

        public ApplicationWithoutNameException(String message) {
            super(message);
        }
    }

    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception, baby")
    class ApplicationException extends RuntimeException {
        public ApplicationException(Throwable cause) {
            super(cause);
        }
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No applications found")
    class NoApplicationsFoundException extends RuntimeException {
        public NoApplicationsFoundException(Throwable cause) {
            super(cause);
        }
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Application not found")
    class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Throwable cause) {
            super(cause);
        }
    }
}
