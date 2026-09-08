/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent.util

import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.HttpsHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService

/**
 * Utility class for handling health check conversion across load balancer caching agents.
 * Consolidates duplicate health check handling logic.
 */
class HealthCheckHelper {

  /**
   * Handle HttpHealthCheck and populate GoogleBackendService with health check data.
   * Works with both single backend service and list of backend services.
   */
  static void handleHttpHealthCheck(HttpHealthCheck httpHealthCheck, GoogleBackendService service) {
    if (!httpHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
      port: httpHealthCheck.port,
      requestPath: httpHealthCheck.requestPath,
      checkIntervalSec: httpHealthCheck.checkIntervalSec,
      timeoutSec: httpHealthCheck.timeoutSec,
      unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
      healthyThreshold: httpHealthCheck.healthyThreshold
    )
  }

  /**
   * Handle HttpHealthCheck for a list of GoogleBackendServices.
   */
  static void handleHttpHealthCheck(HttpHealthCheck httpHealthCheck, List<GoogleBackendService> googleBackendServices) {
    if (!httpHealthCheck) {
      return
    }
    googleBackendServices.each { GoogleBackendService service ->
      service.healthCheck = new GoogleHealthCheck(
        name: httpHealthCheck.name,
        healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
        port: httpHealthCheck.port,
        requestPath: httpHealthCheck.requestPath,
        checkIntervalSec: httpHealthCheck.checkIntervalSec,
        timeoutSec: httpHealthCheck.timeoutSec,
        unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
        healthyThreshold: httpHealthCheck.healthyThreshold
      )
    }
  }

  /**
   * Handle HttpsHealthCheck and populate GoogleBackendService with health check data.
   */
  static void handleHttpsHealthCheck(HttpsHealthCheck httpsHealthCheck, GoogleBackendService service) {
    if (!httpsHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpsHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTPS,
      port: httpsHealthCheck.port,
      requestPath: httpsHealthCheck.requestPath,
      checkIntervalSec: httpsHealthCheck.checkIntervalSec,
      timeoutSec: httpsHealthCheck.timeoutSec,
      unhealthyThreshold: httpsHealthCheck.unhealthyThreshold,
      healthyThreshold: httpsHealthCheck.healthyThreshold
    )
  }

  /**
   * Handle HttpsHealthCheck for a list of GoogleBackendServices.
   */
  static void handleHttpsHealthCheck(HttpsHealthCheck httpsHealthCheck, List<GoogleBackendService> googleBackendServices) {
    if (!httpsHealthCheck) {
      return
    }
    googleBackendServices.each { GoogleBackendService service ->
      service.healthCheck = new GoogleHealthCheck(
        name: httpsHealthCheck.name,
        healthCheckType: GoogleHealthCheck.HealthCheckType.HTTPS,
        port: httpsHealthCheck.port,
        requestPath: httpsHealthCheck.requestPath,
        checkIntervalSec: httpsHealthCheck.checkIntervalSec,
        timeoutSec: httpsHealthCheck.timeoutSec,
        unhealthyThreshold: httpsHealthCheck.unhealthyThreshold,
        healthyThreshold: httpsHealthCheck.healthyThreshold
      )
    }
  }

  /**
   * Handle the new-style HealthCheck (supports multiple protocols) and populate GoogleBackendService.
   */
  static void handleHealthCheck(HealthCheck healthCheck, GoogleBackendService service) {
    if (!healthCheck) {
      return
    }

    Integer port = null
    GoogleHealthCheck.HealthCheckType hcType = null
    String requestPath = null

    if (healthCheck.tcpHealthCheck) {
      port = healthCheck.tcpHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.TCP
    } else if (healthCheck.sslHealthCheck) {
      port = healthCheck.sslHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.SSL
    } else if (healthCheck.httpHealthCheck) {
      port = healthCheck.httpHealthCheck.port
      requestPath = healthCheck.httpHealthCheck.requestPath
      hcType = GoogleHealthCheck.HealthCheckType.HTTP
    } else if (healthCheck.httpsHealthCheck) {
      port = healthCheck.httpsHealthCheck.port
      requestPath = healthCheck.httpsHealthCheck.requestPath
      hcType = GoogleHealthCheck.HealthCheckType.HTTPS
    } else if (healthCheck.http2HealthCheck) {
      port = healthCheck.http2HealthCheck.port
      requestPath = healthCheck.http2HealthCheck.requestPath
      hcType = GoogleHealthCheck.HealthCheckType.HTTP2
    } else if (healthCheck.grpcHealthCheck) {
      port = healthCheck.grpcHealthCheck.port
      requestPath = healthCheck.grpcHealthCheck.grpcServiceName
      hcType = GoogleHealthCheck.HealthCheckType.GRPC
    }

    if (port != null && hcType != null) {
      service.healthCheck = new GoogleHealthCheck(
        name: healthCheck.name,
        requestPath: requestPath,
        selfLink: healthCheck.selfLink,
        port: port,
        healthCheckType: hcType,
        checkIntervalSec: healthCheck.checkIntervalSec,
        timeoutSec: healthCheck.timeoutSec,
        unhealthyThreshold: healthCheck.unhealthyThreshold,
        healthyThreshold: healthCheck.healthyThreshold,
        region: healthCheck.region
      )
    }
  }

  /**
   * Handle HealthCheck for a list of GoogleBackendServices.
   */
  static void handleHealthCheck(HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
    googleBackendServices.each { service ->
      handleHealthCheck(healthCheck, service)
    }
  }
}
