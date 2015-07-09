/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.events.EchoEventListener
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.model.Event
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/**
 * Ensures that beans that implement EchoEventListener are discovered by the configuration automatically
 */
@ContextConfiguration(classes = [EchoCoreConfig, DummyListenerConfig])
class BeanDiscoverySpec extends Specification {

    @Autowired
    EventPropagator eventPropagator

    @Autowired
    ApplicationContext applicationContext

    void 'can discover additional beans'() {
        expect:
        eventPropagator.listeners.size() == 2
        eventPropagator.listeners.first().class == DummyListener
    }
}

@Configuration
class DummyListenerConfig {

    @Bean
    DummyListener listener() {
        new DummyListener()
    }

    @Bean
    DummyListener listener2() {
        new DummyListener()
    }

}

class DummyListener implements EchoEventListener {
    @Override
    void processEvent(Event event) {
        // do nothing
    }
}

