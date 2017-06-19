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

import com.netflix.spinnaker.echo.config.RestUrls
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.rest.RestService
import spock.lang.Specification
import spock.lang.Subject


class RestEventListenerSpec extends Specification {

  @Subject
  RestEventListener listener = new RestEventListener()
  Event event = new Event(content: ['uno': 'dos'])
  RestService restService

  void setup() {
    listener.eventName = 'defaultEvent'
    listener.fieldName = 'defaultField'
    listener.restUrls = new RestUrls()
    listener.restEventTemplateEngine = new SimpleEventTemplateEngine()
    restService = Mock(RestService)
  }

  void 'render template when template is set'() {
    given:
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          template: '{"myCustomEventField":{{event}} }',
          wrap    : true
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it.myCustomEventField == listener.mapper.convertValue(event, Map)
    })
  }

  void 'wraps events when wrap is set'() {
    given:
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          wrap: true
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it.eventName == listener.eventName &&
        it.defaultField == listener.mapper.convertValue(event, Map)
    })
  }

  void 'can overwrite wrap field for'() {
    given:
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          wrap     : true,
          fieldName: 'myField',
          eventName: 'myEventName'
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it.eventName == 'myEventName' &&
        it.defaultField == null &&
        it.myField == listener.mapper.convertValue(event, Map)
    })
  }

  void 'can disable wrapping of events'() {
    given:
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          wrap: false
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it == listener.mapper.convertValue(event, Map)
    })
  }

  void 'sends events to multiple hosts'() {
    given:
    RestService restService2 = Mock(RestService)
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          wrap: false
        ]
      ],
      [
        client: restService2,
        config: [
          wrap: false
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it == listener.mapper.convertValue(event, Map)
    })
    1 * restService2.recordEvent({
      it == listener.mapper.convertValue(event, Map)
    })
  }

  void 'exception in sending event to one host does not affect second host'() {
    given:
    RestService restService2 = Mock(RestService)
    listener.restUrls.services = [
      [
        client: restService,
        config: [
          wrap: false
        ]
      ],
      [
        client: restService2,
        config: [
          wrap: false
        ]
      ]
    ]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent(_) >> { throw new Exception() }
    1 * restService2.recordEvent({
      it == listener.mapper.convertValue(event, Map)
    })
  }
}
