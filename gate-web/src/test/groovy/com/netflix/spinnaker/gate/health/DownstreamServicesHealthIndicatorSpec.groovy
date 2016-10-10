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
import org.springframework.boot.actuate.health.Health
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import retrofit.RetrofitError
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
    1 * healthCheckableService.health() >> { [:] }
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
      throw new RetrofitError("Unable to connect", "http://localhost", null, null, null, null, null)
    }

    healthIndicator.failuresByService.get() == [
      "test": "Unable to connect (url: http://localhost)"
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
}
