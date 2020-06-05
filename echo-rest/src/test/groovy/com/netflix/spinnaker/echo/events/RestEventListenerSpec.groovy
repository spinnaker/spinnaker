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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.config.RestProperties
import com.netflix.spinnaker.echo.config.RestUrls
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.rest.RestService
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Subject


class RestEventListenerSpec extends Specification {

  @Subject
  RestEventListener listener = new RestEventListener(null, null, new NoopRegistry(), new RetrySupport())
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
    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setTemplate('{"myCustomEventField":{{event}} }')
    config.setWrap(true)

    RestUrls.Service service = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    listener.restUrls.services = [service]

    when:
    listener.processEvent(event)

    then:
    1 * restService.recordEvent({
      it.myCustomEventField == listener.mapper.convertValue(event, Map)
    })
  }

  void 'wraps events when wrap is set'() {
    given:
    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setWrap(true)

    RestUrls.Service service = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    listener.restUrls.services = [service]

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
    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setWrap(true)
    config.setFieldName('myField')
    config.setEventName('myEventName')

    RestUrls.Service service = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    listener.restUrls.services = [service]

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
    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setWrap(false)

    RestUrls.Service service = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    listener.restUrls.services = [service]

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

    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setWrap(false)

    RestUrls.Service service1 = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    RestUrls.Service service2 = RestUrls.Service.builder()
      .client(restService2)
      .config(config)
      .build()

    listener.restUrls.services = [service1, service2]

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

    RestProperties.RestEndpointConfiguration config = new RestProperties.RestEndpointConfiguration()
    config.setWrap(false)
    config.setRetryCount(3)

    RestUrls.Service service1 = RestUrls.Service.builder()
      .client(restService)
      .config(config)
      .build()

    RestUrls.Service service2 = RestUrls.Service.builder()
      .client(restService2)
      .config(config)
      .build()

    listener.restUrls.services = [service1, service2]

    when:
    listener.processEvent(event)

    then:
    config.retryCount * restService.recordEvent(_) >> { throw new Exception() }
    1 * restService2.recordEvent({
      it == listener.mapper.convertValue(event, Map)
    })
  }
}
