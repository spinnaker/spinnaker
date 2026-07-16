/*
 * Copyright 2026 Harness, Inc.
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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingScheme
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleRegionalExternalNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials

/**
 * Caches regional external passthrough Network Load Balancers from GCP regional resources.
 *
 * <p>The shared passthrough base owns the forwarding-rule/backend-service/group-health graph walk;
 * this subclass keeps the EXTERNAL TCP/UDP ownership and regional health-check conversion.
 */
class GoogleRegionalExternalNetworkLoadBalancerCachingAgent
  extends AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent<GoogleRegionalExternalNetworkLoadBalancer> {

  GoogleRegionalExternalNetworkLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
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

  static boolean isRegionalExternalNetworkPassthroughRule(ForwardingRule forwardingRule) {
    // Regional external proxy LBs also use EXTERNAL forwarding rules, but they point at targets.
    // This agent only owns passthrough rules that point directly at a regional backend service.
    GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
      forwardingRule, "EXTERNAL", ["TCP", "UDP"] as Set<String>)
  }

  @Override
  String instrumentationPrefix() {
    "RegionalExternalNetworkLoadBalancerCaching"
  }

  @Override
  boolean ownsForwardingRule(ForwardingRule forwardingRule) {
    isRegionalExternalNetworkPassthroughRule(forwardingRule)
  }

  @Override
  GoogleRegionalExternalNetworkLoadBalancer newLoadBalancer(ForwardingRule forwardingRule) {
    new GoogleRegionalExternalNetworkLoadBalancer(
      name: forwardingRule.name,
      account: accountName,
      region: Utils.getLocalName(forwardingRule.getRegion()),
      createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
      ipAddress: forwardingRule.IPAddress,
      ipProtocol: forwardingRule.IPProtocol,
      ports: forwardingRule.ports,
      loadBalancingScheme: GoogleLoadBalancingScheme.valueOf(forwardingRule.getLoadBalancingScheme()),
      network: forwardingRule.getNetwork(),
      networkTier: forwardingRule.getNetworkTier(),
      healths: [],
    )
  }

  @Override
  Object fetchHealthCheckContext() {
    GCEUtil.fetchRegionalHealthChecks(this, compute, project, region)
  }

  @Override
  void attachHealthChecks(BackendService backendService, GoogleRegionalExternalNetworkLoadBalancer googleLoadBalancer, Object healthCheckContext) {
    backendService.healthChecks?.each { String healthCheckURL ->
      def healthCheckName = Utils.getLocalName(healthCheckURL)
      HealthCheck healthCheck = healthCheckContext.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
      GoogleRegionalExternalNetworkLoadBalancerCachingAgent.handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
    }
  }

  @Override
  String wrongSchemeMessage() {
    "Not responsible for on demand caching of non-EXTERNAL regional passthrough load balancers."
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
