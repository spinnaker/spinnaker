package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.events.EchoEventListener
import com.netflix.spinnaker.echo.events.EventPropagator
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Event Propagator
 */
@Configuration
@CompileStatic
@ComponentScan('com.netflix.spinnaker.echo.events')
class EchoCoreConfig {

    @Autowired ApplicationContext context

    @Bean
    EventPropagator propagator() {
        EventPropagator instance = new EventPropagator()
        context.getBeansOfType(EchoEventListener).values().each {
            instance.addListener(it)
        }
        instance
    }

}
