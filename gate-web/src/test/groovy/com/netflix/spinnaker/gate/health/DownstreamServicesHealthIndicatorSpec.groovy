/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.gate.health

import com.netflix.spinnaker.gate.services.internal.HealthCheckableService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import org.springframework.boot.actuate.health.Health
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest

class DownstreamServicesHealthIndicatorSpec extends Specification {
  def healthCheckableService = Mock(HealthCheckableService)

  def "should check downstream health only once if successful"() {
    given:
    def healthIndicator = new DownstreamServicesHealthIndicator(["test": healthCheckableService])

    when:
    healthIndicator.checkDownstreamServiceHealth()

    then:
    1 * healthCheckableService.health() >> { Calls.response([:]) }
    healthIndicator.skipDownstreamServiceChecks.get()

    when:
    healthIndicator.checkDownstreamServiceHealth()

    then:
    0 * healthCheckableService.health()

  }

  def "should record failures if health check unsuccessful"() {
    given:
    def healthIndicator = new DownstreamServicesHealthIndicator(["test": healthCheckableService])

    when:
    healthIndicator.checkDownstreamServiceHealth()

    then:
    1 * healthCheckableService.health() >> {
      throw new SpinnakerServerException( "Unable to connect", new SpinnakerServerException(new Request.Builder().url("http://localhost").build()))
    }

    healthIndicator.failuresByService.get() == [
      "test": "Unable to connect (url: http://localhost/)"
    ]
    !healthIndicator.skipDownstreamServiceChecks.get()
  }

  @Unroll
  def "should only include downstream service check results if `checkDownstreamServices` == true"() {
    given:
    def healthIndicator = new DownstreamServicesHealthIndicator(["test": healthCheckableService])
    def healthBuilder = Mock(Health.Builder)
    def httpServletRequest = Mock(HttpServletRequest)

    and:
    RequestContextHolder.setRequestAttributes(
      new ServletWebRequest(httpServletRequest)
    )

    1 * httpServletRequest.getParameter("checkDownstreamServices") >> { checkDownstreamServices }
    1 * httpServletRequest.getParameter("downstreamServices") >> { downstreamServices }
    healthIndicator.skipDownstreamServiceChecks.set(true)

    when:
    healthIndicator.doHealthCheck(healthBuilder)

    then:
    upInvocations * healthBuilder.up()

    healthIndicator.skipDownstreamServiceChecks.get() == expectedSkipDownstreamServiceChecks

    where:
    checkDownstreamServices | downstreamServices || upInvocations || expectedSkipDownstreamServiceChecks
    "true"                  | "true"             || 1             || false
    "false"                 | "true"             || 1             || true
    "false"                 | "false"            || 0             || true
    null                    | null               || 0             || true
  }

  def "should include error details if `checkDownstreamServices` == true and failures are present"() {
    given:
    def healthIndicator = new DownstreamServicesHealthIndicator(["test": healthCheckableService])
    def healthBuilder = Mock(Health.Builder)
    def httpServletRequest = Mock(HttpServletRequest)

    and:
    RequestContextHolder.setRequestAttributes(
      new ServletWebRequest(httpServletRequest)
    )

    1 * httpServletRequest.getParameter("checkDownstreamServices") >> { true }
    1 * httpServletRequest.getParameter("downstreamServices") >> { true }
    healthIndicator.failuresByService.set(["test": "Unable to connect (url: http://localhost)"])

    when:
    healthIndicator.doHealthCheck(healthBuilder)

    then:
    1 * healthBuilder.down() >> { healthBuilder }
    1 * healthBuilder.withDetail("errors", ["test": "Unable to connect (url: http://localhost)"])
  }

  def "should noop if no request attributes are available"() {
    given:
    def healthIndicator = new DownstreamServicesHealthIndicator(["test": healthCheckableService])
    def healthBuilder = Mock(Health.Builder)

    and:
    RequestContextHolder.setRequestAttributes(null)

    when:
    healthIndicator.doHealthCheck(healthBuilder)

    then:
    0 * _
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
      retrofit2.Response.error(
        status,
        ResponseBody.create(
          MediaType.parse("application/json"), "{ \"message\": \"Unable to connect\" }"));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
