/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.kork.web.interceptors

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.DefaultTimer
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class MetricsInterceptorSpec extends Specification {
  def "should store current time as request attribute"() {
    given:
    def request = Mock(HttpServletRequest)
    def interceptor = Spy(MetricsInterceptor, constructorArgs: [null, null, null, null]) {
      1 * getNanoTime() >> { return nanoTime }
    }

    when:
    interceptor.preHandle(request, null, null)

    then:
    1 * request.setAttribute(MetricsInterceptor.TIMER_ATTRIBUTE, nanoTime)

    where:
    nanoTime = 1000L
  }

  @Unroll
  def "should create metric with expected tags"() {
    given:
    def request = Mock(HttpServletRequest) {
      1 * getAttribute(MetricsInterceptor.TIMER_ATTRIBUTE) >> { return startTime }
      1 * getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) >> templateVariables
      1 * getContentLengthLong() >> { 10L }
      0 * _
    }
    def response = Mock(HttpServletResponse) {
      1 * getStatus() >> { return status }
      0 * _
    }

    and:
    def registry = new StubRegistry()
    def handler = new HandlerMethod(new Example(), Example.getMethod("get"))
    def interceptor = Spy(MetricsInterceptor, constructorArgs: [
      registry, metric, variablesToTag, null
    ]) {
      1 * getNanoTime() >> { return endTime }
    }

    when:
    interceptor.afterCompletion(request, response, handler, exception)

    then:
    registry.id.tags().collectEntries { [it.key(), it.value()] } == expectedTags
    registry.timer.totalTime() == (endTime - startTime)
    registry.timer.count() == 1

    where:
    exception                  | variablesToTag | expectedTags
    null                       | []             | [success: "true", statusCode: "200", status: "2xx", "method": "get", controller: "Example"]
    new NullPointerException() | []             | [success: "false", statusCode: "500", status: "5xx", "method": "get", controller: "Example", cause: "NullPointerException"]
    null                       | ["account"]    | [success: "true", statusCode: "200", status: "2xx", "method": "get", controller: "Example", "account": "test"]

    templateVariables = ["account": "test", "empty": "empty"]
    startTime = 0L
    endTime = 2000L
    status = 200
    metric = "metric"
  }

  def "should skip excluded controllers"() {
    given:
    def handler = new HandlerMethod(new Example(), Example.getMethod("get"))
    def interceptor = new MetricsInterceptor(null, null, null, [handler.bean.class.simpleName])

    expect:
    interceptor.afterCompletion(null, null, handler, null)
  }

  class Example {
    void get() {}
  }

  class StubRegistry implements Registry {
    Id id = null
    Timer timer = null

    @Delegate
    DefaultRegistry defaultRegistry = new DefaultRegistry()

    @Override
    Timer timer(Id id) {
      this.id = id
      this.timer = new DefaultTimer(null, id)
      return timer
    }
  }
}
