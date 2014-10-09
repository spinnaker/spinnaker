/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.model.Event
import org.springframework.messaging.simp.SimpMessagingTemplate
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * tests for StompEventListener
 */
@Unroll
class StompEventListenerSpec extends Specification {

    @Subject
    StompEventListener listener = new StompEventListener()
    Event e

    void setup() {
        listener.template = Mock(SimpMessagingTemplate)
        e = new Event(details: [])
    }

    void 'messages are sent to #type topic'() {
        given:
        e.details[type] = 'mytype'

        when:
        listener.processEvent(e)

        then:
        1 * listener.template.convertAndSend("/topic/$type/mytype", _)

        where:
        type << ['application', 'source', 'type']
    }

    void 'no event is fired into the websocket endpoint if no application is specified'() {
        expect:
        e.details.application == null

        when:
        listener.processEvent(e)

        then:
        0 * listener.template.convertAndSend( { it.startsWith('/topic/application') } , _)
    }

    void 'all events are relayed to events topic'() {
        when:
        listener.processEvent(e)

        then:
        1 * listener.template.convertAndSend('/topic/events', _)
    }

}
