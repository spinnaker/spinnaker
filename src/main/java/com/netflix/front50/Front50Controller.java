package com.netflix.front50;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Configuration
@EnableAutoConfiguration
@ComponentScan
public class Front50Controller {

    public static void main(String[] args) {
        SpringApplication.run(Front50Controller.class, args);
    }

    @RequestMapping(method = RequestMethod.GET)
    public Application index() {
        return new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
                "netflix.com application", "Standalone Application", null, null, 1265752693581l, 1265752693581l);
    }
}
