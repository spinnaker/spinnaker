/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Canonical

@Canonical
class GoogleHealthCheck {
  String name
  String requestPath
  String grpcServiceName
  String selfLink
  Integer port
  String portSpecification
  HealthCheckType healthCheckType

  // Attributes
  int checkIntervalSec
  int timeoutSec
  int unhealthyThreshold
  int healthyThreshold

  /**
   * Specifies the GCP endpoint 'family' this health check originated from.
   *
   * There are currently three different sets of health check endpoints:
   * 1. /{project}/global/httpHealthChecks/{healthCheckname}
   * 2. /{project}/global/httpsHealthChecks/{healthCheckname}
   * 3. /{project}/global/healthChecks/{healthCheckname}
   *
   * Endpoint (3) can return HTTP and HTTPS endpoints, similar to endpoints (1) and (2).
   * Since we cache health checks from all three endpoints, we need to specify which
   * endpoint we got the health check from so we don't have key collisions during caching.
   * That's what this field does.
   */
  HealthCheckKind kind

  String region

  /**
   * Name of the GCP certificate, if HTTPS/SSL.
   */
  String certificate

  @JsonIgnore
  View getView() {
    new View(this)
  }

  /**
   * Health check endpoint of the form '{PROTO}:{PORT}{PATH}'.
   */
  String getTarget() {
    def portDisplay = portSpecification == 'USE_SERVING_PORT' ? 'USE_SERVING_PORT' : port
    if (!portDisplay) {
      return null
    }

    switch (healthCheckType) {
      case HealthCheckType.HTTP:
        return "HTTP:${portDisplay}${this.requestPath ?: '/'}"
        break
      case HealthCheckType.HTTPS:
        return "HTTPS:${portDisplay}${this.requestPath ?: '/'}"
        break
      case HealthCheckType.HTTP2:
        return "HTTP2:${portDisplay}${this.requestPath ?: '/'}"
        break
      case HealthCheckType.GRPC:
        return "GRPC:${portDisplay}${this.grpcServiceName ?: ''}"
        break
      case HealthCheckType.SSL:
        return "SSL:${portDisplay}"
        break
      case HealthCheckType.TCP:
        return "TCP:${portDisplay}"
        break
      case HealthCheckType.UDP:
        return "UDP:${portDisplay}"
        break
      default:
        break
    }
  }


  @Canonical
  class View implements Serializable {
    String name
    HealthCheckType healthCheckType
    int interval
    int timeout
    int unhealthyThreshold
    int healthyThreshold
    Integer port
    String portSpecification
    String requestPath
    String grpcServiceName
    String selfLink
    String kind
    String target
    String region

    View(GoogleHealthCheck googleHealthCheck){
      name = googleHealthCheck.name
      healthCheckType = googleHealthCheck.healthCheckType
      interval = googleHealthCheck.checkIntervalSec
      timeout = googleHealthCheck.timeoutSec
      unhealthyThreshold = googleHealthCheck.unhealthyThreshold
      healthyThreshold = googleHealthCheck.healthyThreshold
      port = googleHealthCheck.port
      portSpecification = googleHealthCheck.portSpecification
      requestPath = googleHealthCheck.requestPath
      grpcServiceName = googleHealthCheck.grpcServiceName
      selfLink = googleHealthCheck.selfLink
      kind = googleHealthCheck.kind
      target = googleHealthCheck.target
      region = googleHealthCheck.region
    }

  }

  static enum HealthCheckType {
    HTTP,
    HTTPS,
    GRPC,
    HTTP2,
    SSL,
    TCP,
    UDP
  }

  // Note: This enum has non-standard style constants because we use these constants as strings directly
  // in the redis cache keys for health checks, where we want to avoid underscores and camelcase is the norm.
  static enum HealthCheckKind {
    healthCheck,
    httpHealthCheck,
    httpsHealthCheck,
    http2HealthCheck,
    grpcHealthCheck
  }
}
