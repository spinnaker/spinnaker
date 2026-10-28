/*
 * Copyright 2026 Google, LLC.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import com.google.api.services.compute.model.HealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import javax.annotation.Nullable;

/**
 * Converts a Compute {@link HealthCheck} referenced by a load balancer backend service into the
 * cached {@link GoogleHealthCheck} model. Shared by the load balancer caching agents so they parse
 * health check types and port specifications the same way.
 */
final class GoogleLoadBalancerHealthCheckSupport {

  static final String USE_SERVING_PORT = "USE_SERVING_PORT";

  private GoogleLoadBalancerHealthCheckSupport() {}

  /**
   * Returns the cached model for a health check, or null when the health check is null, has no
   * recognized type, or has neither a fixed port nor {@code USE_SERVING_PORT}.
   *
   * <p>The gRPC service name is stored in {@code grpcServiceName}; {@code requestPath} is only set
   * for HTTP, HTTPS and HTTP/2 health checks. {@code USE_NAMED_PORT} health checks have no port and
   * are skipped, as before.
   */
  @Nullable
  static GoogleHealthCheck fromHealthCheck(@Nullable HealthCheck healthCheck) {
    if (healthCheck == null) {
      return null;
    }

    Integer port = null;
    String portSpecification = null;
    String requestPath = null;
    String grpcServiceName = null;
    GoogleHealthCheck.HealthCheckType healthCheckType = null;
    if (healthCheck.getTcpHealthCheck() != null) {
      port = healthCheck.getTcpHealthCheck().getPort();
      portSpecification = healthCheck.getTcpHealthCheck().getPortSpecification();
      healthCheckType = GoogleHealthCheck.HealthCheckType.TCP;
    } else if (healthCheck.getSslHealthCheck() != null) {
      port = healthCheck.getSslHealthCheck().getPort();
      portSpecification = healthCheck.getSslHealthCheck().getPortSpecification();
      healthCheckType = GoogleHealthCheck.HealthCheckType.SSL;
    } else if (healthCheck.getHttpHealthCheck() != null) {
      port = healthCheck.getHttpHealthCheck().getPort();
      portSpecification = healthCheck.getHttpHealthCheck().getPortSpecification();
      requestPath = healthCheck.getHttpHealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP;
    } else if (healthCheck.getHttpsHealthCheck() != null) {
      port = healthCheck.getHttpsHealthCheck().getPort();
      portSpecification = healthCheck.getHttpsHealthCheck().getPortSpecification();
      requestPath = healthCheck.getHttpsHealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTPS;
    } else if (healthCheck.getUdpHealthCheck() != null) {
      port = healthCheck.getUdpHealthCheck().getPort();
      portSpecification = healthCheck.getUdpHealthCheck().getPortSpecification();
      healthCheckType = GoogleHealthCheck.HealthCheckType.UDP;
    } else if (healthCheck.getHttp2HealthCheck() != null) {
      port = healthCheck.getHttp2HealthCheck().getPort();
      portSpecification = healthCheck.getHttp2HealthCheck().getPortSpecification();
      requestPath = healthCheck.getHttp2HealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP2;
    } else if (healthCheck.getGrpcHealthCheck() != null) {
      port = healthCheck.getGrpcHealthCheck().getPort();
      portSpecification = healthCheck.getGrpcHealthCheck().getPortSpecification();
      grpcServiceName = healthCheck.getGrpcHealthCheck().getGrpcServiceName();
      healthCheckType = GoogleHealthCheck.HealthCheckType.GRPC;
    }

    if (healthCheckType == null || (port == null && !USE_SERVING_PORT.equals(portSpecification))) {
      return null;
    }

    GoogleHealthCheck googleHealthCheck = new GoogleHealthCheck();
    googleHealthCheck.setName(healthCheck.getName());
    googleHealthCheck.setHealthCheckType(healthCheckType);
    googleHealthCheck.setPort(port);
    googleHealthCheck.setPortSpecification(portSpecification);
    googleHealthCheck.setRequestPath(requestPath);
    googleHealthCheck.setGrpcServiceName(grpcServiceName);
    googleHealthCheck.setCheckIntervalSec(healthCheck.getCheckIntervalSec());
    googleHealthCheck.setTimeoutSec(healthCheck.getTimeoutSec());
    googleHealthCheck.setUnhealthyThreshold(healthCheck.getUnhealthyThreshold());
    googleHealthCheck.setHealthyThreshold(healthCheck.getHealthyThreshold());
    return googleHealthCheck;
  }

  /**
   * Same as {@link #fromHealthCheck(HealthCheck)}, but uses empty strings instead of null for the
   * request path and gRPC service name, matching what the global and passthrough load balancer
   * caching agents have always cached.
   */
  @Nullable
  static GoogleHealthCheck fromHealthCheckWithEmptyDefaults(@Nullable HealthCheck healthCheck) {
    GoogleHealthCheck googleHealthCheck = fromHealthCheck(healthCheck);
    if (googleHealthCheck != null) {
      if (googleHealthCheck.getRequestPath() == null) {
        googleHealthCheck.setRequestPath("");
      }
      if (googleHealthCheck.getGrpcServiceName() == null) {
        googleHealthCheck.setGrpcServiceName("");
      }
    }
    return googleHealthCheck;
  }
}
