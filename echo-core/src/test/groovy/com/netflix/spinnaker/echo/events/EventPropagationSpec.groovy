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
package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.api.events.EventListener
import com.netflix.spinnaker.echo.api.events.Event
import rx.schedulers.Schedulers
import spock.lang.Specification

/**
 * Ensures that events are properly propagated
 */
class EventPropagationSpec extends Specification {

    void 'events are sent to every listener'() {

        given:
        EventPropagator propagator = new EventPropagator()
        propagator.scheduler = Schedulers.immediate()
        EventListener l1 = Mock(EventListener)
        EventListener l2 = Mock(EventListener)

        when:
        propagator.addListener(l1)
        propagator.addListener(l2)

        and:
        propagator.processEvent(new Event())

        then:
        1 * l1.processEvent(_)
        1 * l2.processEvent(_)

    }

}
