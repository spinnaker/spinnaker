package com.netflix.front50;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.front50.exception.NoPrimaryKeyException;
import com.netflix.front50.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.netflix.front50")
public class Front50Controller extends SpringBootServletInitializer {
    static final Logger LOG = LoggerFactory.getLogger(Front50Controller.class);

    @Autowired
    Application application;

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        initializeEnv();
        application.sources(Front50Controller.class);
        return super.configure(application);
    }

    public static void main(String[] args) {
        initializeEnv();
        SpringApplication.run(Front50Controller.class, args);
    }

    @Bean
    public InstanceInfo.InstanceStatus instanceStatus() {
        return InstanceInfo.InstanceStatus.UNKNOWN;
    }

    @RequestMapping(method = RequestMethod.PUT, value="/applications")
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

    @RequestMapping(method = RequestMethod.POST, value="/applications/name/{name}")
    @ResponseBody
    public Application post(@RequestBody Application app) {
        try {
            return application.initialize(app).withName(app.getName()).save();
        } catch (NoPrimaryKeyException e) {
            LOG.error("POST:: cannot create app as name and/or email is missing: " + app, e);
            throw new ApplicationWithoutNameException(e);
        } catch (Throwable thr) {
            LOG.error("POST:: throwable occurred: " + app.getName(), thr);
            throw new ApplicationException(thr);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value="/applications")
    public Collection<Application> index() {
        try {
            return application.findAll();
        } catch (NotFoundException e) {
            LOG.error("GET(/applications) -> NotFoundException occurred: ", e);
            throw new NoApplicationsFoundException(e);
        } catch (Throwable thr) {
            LOG.error("GET(/applications) -> Throwable occurred: ", thr);
            throw new ApplicationException(thr);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, value = "/applications/name/{name}")
    public void delete(@PathVariable String name) {
        try {
            application.initialize(new Application().withName(name)).delete();
        } catch (NoPrimaryKeyException e) {
            LOG.error("GET(/name/{name}) -> NotFoundException occurred for app: " + name, e);
            throw new ApplicationNotFoundException(e);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/applications/name/{name}")
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

    private static void initializeEnv() {
        if (System.getProperty("netflix.environment") == null) {
            System.setProperty("netflix.environment", "test");
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
