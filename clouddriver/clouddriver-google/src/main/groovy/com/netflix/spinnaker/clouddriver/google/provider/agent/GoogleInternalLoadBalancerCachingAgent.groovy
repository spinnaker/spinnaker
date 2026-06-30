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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.HttpsHealthCheck
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingScheme
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials

class GoogleInternalLoadBalancerCachingAgent
  extends AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent<GoogleInternalLoadBalancer> {

  GoogleInternalLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                         GoogleNamedAccountCredentials credentials,
                                         ObjectMapper objectMapper,
                                         Registry registry,
                                         String region) {
    super(clouddriverUserAgentApplicationName,
      credentials,
      objectMapper,
      registry,
      region)
  }

  static boolean isInternalPassthroughRule(ForwardingRule forwardingRule) {
    GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(forwardingRule, "INTERNAL", null)
  }

  @Override
  String instrumentationPrefix() {
    "InternalLoadBalancerCaching"
  }

  @Override
  boolean ownsForwardingRule(ForwardingRule forwardingRule) {
    isInternalPassthroughRule(forwardingRule)
  }

  @Override
  GoogleInternalLoadBalancer newLoadBalancer(ForwardingRule forwardingRule) {
    new GoogleInternalLoadBalancer(
      name: forwardingRule.name,
      account: accountName,
      region: Utils.getLocalName(forwardingRule.getRegion()),
      createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
      ipAddress: forwardingRule.IPAddress,
      ipProtocol: forwardingRule.IPProtocol,
      ports: forwardingRule.ports,
      loadBalancingScheme: GoogleLoadBalancingScheme.valueOf(forwardingRule.getLoadBalancingScheme()),
      network: forwardingRule.getNetwork(),
      subnet: forwardingRule.getSubnetwork(),
      healths: [],
    )
  }

  @Override
  Object fetchHealthCheckContext() {
    [
      httpHealthChecks : GCEUtil.fetchHttpHealthChecks(this, compute, project),
      httpsHealthChecks: GCEUtil.fetchHttpsHealthChecks(this, compute, project),
      healthChecks     : GCEUtil.fetchHealthChecks(this, compute, project),
    ]
  }

  @Override
  void attachHealthChecks(BackendService backendService, GoogleInternalLoadBalancer googleLoadBalancer, Object healthCheckContext) {
    backendService.healthChecks?.each { String healthCheckURL ->
      def healthCheckName = Utils.getLocalName(healthCheckURL)
      def healthCheckType = Utils.getHealthCheckType(healthCheckURL)
      switch (healthCheckType) {
        case "httpHealthChecks":
          HttpHealthCheck httpHealthCheck = healthCheckContext.httpHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpHealthCheck(httpHealthCheck, googleLoadBalancer.backendService)
          break
        case "httpsHealthChecks":
          HttpsHealthCheck httpsHealthCheck = healthCheckContext.httpsHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpsHealthCheck(httpsHealthCheck, googleLoadBalancer.backendService)
          break
        case "healthChecks":
          HealthCheck healthCheck = healthCheckContext.healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          GoogleInternalLoadBalancerCachingAgent.handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
          break
        default:
          log.warn("Unknown health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
          break
      }
    }
  }

  @Override
  String wrongSchemeMessage() {
    "Not responsible for on demand caching of non-INTERNAL passthrough load balancers."
  }

  private static void handleHttpHealthCheck(HttpHealthCheck httpHealthCheck, GoogleBackendService service) {
    if (!httpHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
      requestPath: httpHealthCheck.requestPath,
      port: httpHealthCheck.port,
      checkIntervalSec: httpHealthCheck.checkIntervalSec,
      timeoutSec: httpHealthCheck.timeoutSec,
      unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
      healthyThreshold: httpHealthCheck.healthyThreshold,
    )
  }

  private static void handleHttpsHealthCheck(HttpsHealthCheck httpsHealthCheck, GoogleBackendService service) {
    if (!httpsHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpsHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTPS,
      requestPath: httpsHealthCheck.requestPath,
      port: httpsHealthCheck.port,
      checkIntervalSec: httpsHealthCheck.checkIntervalSec,
      timeoutSec: httpsHealthCheck.timeoutSec,
      unhealthyThreshold: httpsHealthCheck.unhealthyThreshold,
      healthyThreshold: httpsHealthCheck.healthyThreshold,
    )
  }

  private static void handleHealthCheck(HealthCheck healthCheck, GoogleBackendService service) {
    if (!healthCheck) {
      return
    }
    def port = null
    def hcType = null
    def requestPath = null
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
    } else if (healthCheck.udpHealthCheck) {
      port = healthCheck.udpHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.UDP
    }

    if (port && hcType) {
      service.healthCheck = new GoogleHealthCheck(
        name: healthCheck.name,
        healthCheckType: hcType,
        port: port,
        requestPath: requestPath ?: "",
        checkIntervalSec: healthCheck.checkIntervalSec,
        timeoutSec: healthCheck.timeoutSec,
        unhealthyThreshold: healthCheck.unhealthyThreshold,
        healthyThreshold: healthCheck.healthyThreshold,
      )
    }
  }
}
