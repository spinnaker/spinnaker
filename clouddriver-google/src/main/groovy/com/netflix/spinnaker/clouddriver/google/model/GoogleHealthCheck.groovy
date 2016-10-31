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
  int port
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

  /**
   * Name of the GCP certificate, if HTTPS/SSL.
   */
  String certificate

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements Serializable {
    String name = GoogleHealthCheck.this.name
    HealthCheckType healthCheckType = GoogleHealthCheck.this.healthCheckType
    int interval = GoogleHealthCheck.this.checkIntervalSec
    int timeout = GoogleHealthCheck.this.timeoutSec
    int unhealthyThreshold = GoogleHealthCheck.this.unhealthyThreshold
    int healthyThreshold = GoogleHealthCheck.this.healthyThreshold
    int port = GoogleHealthCheck.this.port
    String requestPath = GoogleHealthCheck.this.requestPath
    String kind = GoogleHealthCheck.this.kind

    String getTarget() {
      GoogleHealthCheck.this.port ?
          "HTTP:${GoogleHealthCheck.this.port}${GoogleHealthCheck.this.requestPath ?: '/'}" :
          null
    }

  }

  static enum HealthCheckType {
    HTTP,
    HTTPS,
    SSL,
    TCP,
    UDP
  }

  // Note: This enum has non-standard style constants because we use these constants as strings directly
  // in the redis cache keys for health checks, where we want to avoid underscores and camelcase is the norm.
  static enum HealthCheckKind {
    healthCheck,
    httpHealthCheck,
    httpsHealthCheck
  }
}
