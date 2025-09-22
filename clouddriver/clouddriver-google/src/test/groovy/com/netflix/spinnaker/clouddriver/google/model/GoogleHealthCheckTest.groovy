/*
 * Copyright 2024 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.model

import spock.lang.Specification
import spock.lang.Unroll

class GoogleHealthCheckTest extends Specification {

  @Unroll
  def "getTarget returns correct target string for #healthCheckType health checks"() {
    given:
    GoogleHealthCheck healthCheck = new GoogleHealthCheck()
    healthCheck.healthCheckType = healthCheckType
    healthCheck.port = port
    
    if (healthCheckType == GoogleHealthCheck.HealthCheckType.GRPC) {
      healthCheck.grpcServiceName = requestPath
    } else {
      healthCheck.requestPath = requestPath
    }

    when:
    String target = healthCheck.getTarget()

    then:
    target == expectedTarget

    where:
    healthCheckType                            | port | requestPath                | expectedTarget
    GoogleHealthCheck.HealthCheckType.HTTP     | 80   | "/health"                  | "HTTP:80/health"
    GoogleHealthCheck.HealthCheckType.HTTP     | 8080 | null                       | "HTTP:8080/"
    GoogleHealthCheck.HealthCheckType.HTTP     | 8080 | ""                         | "HTTP:8080/"
    GoogleHealthCheck.HealthCheckType.HTTPS    | 443  | "/secure"                  | "HTTPS:443/secure"
    GoogleHealthCheck.HealthCheckType.HTTPS    | 8443 | null                       | "HTTPS:8443/"
    GoogleHealthCheck.HealthCheckType.HTTP2    | 8080 | "/api/health"              | "HTTP2:8080/api/health"
    GoogleHealthCheck.HealthCheckType.HTTP2    | 9000 | null                       | "HTTP2:9000/"
    GoogleHealthCheck.HealthCheckType.HTTP2    | 9000 | ""                         | "HTTP2:9000/"
    GoogleHealthCheck.HealthCheckType.GRPC     | 9090 | "com.example.HealthService"| "GRPC:9090com.example.HealthService"
    GoogleHealthCheck.HealthCheckType.GRPC     | 50051| null                       | "GRPC:50051"
    GoogleHealthCheck.HealthCheckType.GRPC     | 50051| ""                         | "GRPC:50051"
    GoogleHealthCheck.HealthCheckType.TCP      | 3306 | null                       | "TCP:3306"
    GoogleHealthCheck.HealthCheckType.SSL      | 636  | null                       | "SSL:636"
    GoogleHealthCheck.HealthCheckType.UDP      | 53   | null                       | "UDP:53"
  }

  @Unroll
  def "getTarget returns null when port is #port for #healthCheckType"() {
    given:
    GoogleHealthCheck healthCheck = new GoogleHealthCheck()
    healthCheck.healthCheckType = healthCheckType
    if (port != null) {
      healthCheck.port = port
    }
    healthCheck.requestPath = "/health"

    when:
    String target = healthCheck.getTarget()

    then:
    target == null

    where:
    healthCheckType                            | port
    GoogleHealthCheck.HealthCheckType.HTTP     | 0
    GoogleHealthCheck.HealthCheckType.HTTPS    | 0
    GoogleHealthCheck.HealthCheckType.HTTP2    | 0
    GoogleHealthCheck.HealthCheckType.GRPC     | 0
    GoogleHealthCheck.HealthCheckType.TCP      | 0
    GoogleHealthCheck.HealthCheckType.SSL      | 0
    GoogleHealthCheck.HealthCheckType.UDP      | 0
  }

  def "getTarget handles null health check type"() {
    given:
    GoogleHealthCheck healthCheck = new GoogleHealthCheck()
    healthCheck.healthCheckType = null
    healthCheck.port = 80
    healthCheck.requestPath = "/health"

    when:
    String target = healthCheck.getTarget()

    then:
    target == null
  }

  def "getTarget returns null when port is not set"() {
    given:
    GoogleHealthCheck healthCheck = new GoogleHealthCheck()
    healthCheck.healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP
    healthCheck.requestPath = "/health"
    // port is not set, should default to null/0

    when:
    String target = healthCheck.getTarget()

    then:
    target == null
  }

  def "getView returns correct view with all health check types"() {
    given:
    GoogleHealthCheck healthCheck = new GoogleHealthCheck()
    healthCheck.name = "test-health-check"
    healthCheck.healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP2
    healthCheck.checkIntervalSec = 30
    healthCheck.timeoutSec = 5
    healthCheck.unhealthyThreshold = 3
    healthCheck.healthyThreshold = 2
    healthCheck.port = 8080
    healthCheck.requestPath = "/api/health"
    healthCheck.selfLink = "https://compute.googleapis.com/compute/v1/projects/test/global/healthChecks/test-health-check"
    healthCheck.kind = GoogleHealthCheck.HealthCheckKind.http2HealthCheck
    healthCheck.region = "us-central1"

    when:
    GoogleHealthCheck.View view = healthCheck.getView()

    then:
    view.name == "test-health-check"
    view.healthCheckType == GoogleHealthCheck.HealthCheckType.HTTP2
    view.interval == 30
    view.timeout == 5
    view.unhealthyThreshold == 3
    view.healthyThreshold == 2
    view.port == 8080
    view.requestPath == "/api/health"
    view.selfLink == "https://compute.googleapis.com/compute/v1/projects/test/global/healthChecks/test-health-check"
    view.kind == "http2HealthCheck"
    view.target == "HTTP2:8080/api/health"
    view.region == "us-central1"
  }

  def "HealthCheckKind enum contains all expected values"() {
    expect:
    GoogleHealthCheck.HealthCheckKind.values().collect { it.name() }.containsAll([
      "healthCheck",
      "httpHealthCheck", 
      "httpsHealthCheck",
      "http2HealthCheck",
      "grpcHealthCheck"
    ])
  }

  def "HealthCheckType enum contains all expected values"() {
    expect:
    GoogleHealthCheck.HealthCheckType.values().collect { it.name() }.containsAll([
      "HTTP",
      "HTTPS", 
      "GRPC",
      "HTTP2",
      "SSL",
      "TCP",
      "UDP"
    ])
  }
}
