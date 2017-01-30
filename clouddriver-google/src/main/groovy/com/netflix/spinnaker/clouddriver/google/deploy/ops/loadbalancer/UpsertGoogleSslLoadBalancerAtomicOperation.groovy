/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertGoogleSslLoadBalancerAtomicOperation extends UpsertGoogleLoadBalancerAtomicOperation {
  private static final String BASE_PHASE = "UPSERT_SSL_LOAD_BALANCER"
  public static final String TARGET_SSL_PROXY_NAME_SUFFIX = "target-ssl-proxy"

  private final UpsertGoogleLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  UpsertGoogleSslLoadBalancerAtomicOperation(UpsertGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "region": "global", "portRange": "443", "ipProtocol": "TCP", "credentials" : "my-account-name", "loadBalancerName" : "testlb", "certificate": "my-google-cert", "backendService": {"name": "default-backend-service", "sessionAffinity": "NONE", "backends": [], "healthCheck": {"name": "basic-check", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1, "healthCheckType": "TCP"}}, "loadBalancerType": "SSL"}} ]' localhost:7002/gce/ops
   *
   * @param priorOutputs
   * @return
   */
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.loadBalancerName "

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    GoogleHealthCheck descriptionHealthCheck = description.backendService.healthCheck
    String backendServiceName = description.backendService.name
    String healthCheckName = descriptionHealthCheck.name

    // Set some default values that will be useful when doing comparisons.
    description.ipProtocol = description.ipProtocol ?: Constants.DEFAULT_IP_PROTOCOL

    ForwardingRule existingForwardingRule
    TargetSslProxy existingTargetProxy
    BackendService existingBackendService
    HealthCheck existingHealthCheck // Could be any one of Http, Https, Ssl, or Tcp.

    // We first devise a plan by setting all of these flags.
    boolean needToUpdateTargetProxy = false
    boolean needToUpdateBackendService = false
    boolean needToUpdateHealthCheck = false

    // Check if there already exists a forwarding rule with the requested name.
    existingForwardingRule = safeRetry.doRetry(
      { timeExecute(
            compute.globalForwardingRules().get(project, description.loadBalancerName),
            "compute.globalForwardingRules",
            TAG_SCOPE, SCOPE_GLOBAL) },
      'Get',
      "Global forwarding rule ${description.loadBalancerName}",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    ) as ForwardingRule
    String targetProxyName = "${description.loadBalancerName}-${TARGET_SSL_PROXY_NAME_SUFFIX}"
    if (existingForwardingRule) {
      // Fetch the target proxy.
      targetProxyName = GCEUtil.getLocalName(existingForwardingRule.target)
      existingTargetProxy = safeRetry.doRetry(
        { timeExecute(
              compute.targetSslProxies().get(project, targetProxyName),
              "compute.targetSslProxies.get",
              TAG_SCOPE, SCOPE_GLOBAL)},
        'Get',
        "Target ssl proxy ${targetProxyName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        [404]
      ) as TargetSslProxy
    }

    if (existingTargetProxy) {
      needToUpdateTargetProxy = backendServiceName != GCEUtil.getLocalName(existingTargetProxy.getService()) ||
        description.certificate != GCEUtil.getLocalName(existingTargetProxy.getSslCertificates()[0])
    }

    existingBackendService = safeRetry.doRetry(
      { timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendServices.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      'Get',
      "Global backend service $backendServiceName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    ) as BackendService
    if (existingBackendService) {
      Boolean differentHealthChecks = existingBackendService.getHealthChecks().collect { GCEUtil.getLocalName(it) } != [healthCheckName]
      Boolean differentSessionAffinity = GoogleSessionAffinity.valueOf(existingBackendService.getSessionAffinity()) != description.backendService.sessionAffinity ||
        existingBackendService.getAffinityCookieTtlSec() != description.backendService.affinityCookieTtlSec
      needToUpdateBackendService = differentHealthChecks || differentSessionAffinity
    }

    // Note: SSL LBs only use HealthCheck objects, _not_ Http(s)HealthChecks. The actual check (i.e. Ssl, Tcp, Http(s))
    // is nested in a field inside the HealthCheck object.
    existingHealthCheck = safeRetry.doRetry(
      { timeExecute(
            compute.healthChecks().get(project, healthCheckName),
            "compute.healthChecks.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      'Get',
      "Health check $healthCheckName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    ) as HealthCheck

    needToUpdateHealthCheck = existingHealthCheck && GCEUtil.healthCheckShouldBeUpdated(existingHealthCheck, descriptionHealthCheck)

    // Now we start phase 2 of our plan -- upsert all the components.
    def healthCheckOp = null
    if (!existingHealthCheck) {
      task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."
      def newHealthCheck = GCEUtil.createNewHealthCheck(descriptionHealthCheck)
      healthCheckOp = safeRetry.doRetry(
        { timeExecute(
              compute.healthChecks().insert(project, newHealthCheck as HealthCheck),
              "compute.healthChecks.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Insert',
        "Health check $healthCheckName",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    } else if (existingHealthCheck && needToUpdateHealthCheck) {
      task.updateStatus BASE_PHASE, "Updating health check $healthCheckName..."
      GCEUtil.updateExistingHealthCheck(existingHealthCheck, descriptionHealthCheck)
      healthCheckOp = safeRetry.doRetry(
        { timeExecute(
              compute.healthChecks().update(project, healthCheckName, existingHealthCheck as HealthCheck),
              "compute.healthChecks.update",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Update',
        "Health check $healthCheckName",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    }
    if (healthCheckOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, healthCheckOp.getName(),
        null, task, "health check " + healthCheckName, BASE_PHASE)
    }

    def backendServiceOp = null
    if (!existingBackendService) {
      task.updateStatus BASE_PHASE, "Creating backend service ${description.backendService.name}..."
      BackendService bs = new BackendService(
        name: backendServiceName,
        healthChecks: [GCEUtil.buildHealthCheckUrl(project, healthCheckName)],
        sessionAffinity: description.backendService.sessionAffinity ?: 'NONE',
        affinityCookieTtlSec: description.backendService.affinityCookieTtlSec,
        loadBalancingScheme: 'EXTERNAL',
        protocol: description.ipProtocol
      )
      backendServiceOp = safeRetry.doRetry(
        { timeExecute(
              compute.backendServices().insert(project, bs),
              "compute.backendServices.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Insert',
        "Backend service $description.backendService.name",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    } else if (existingBackendService && needToUpdateBackendService) {
      task.updateStatus BASE_PHASE, "Upating backend service ${description.backendService.name}..."
      existingBackendService.healthChecks = [GCEUtil.buildHealthCheckUrl(project, healthCheckName)]
      existingBackendService.sessionAffinity = description.backendService.sessionAffinity ?: 'NONE'
      existingBackendService.affinityCookieTtlSec = description.backendService.affinityCookieTtlSec
      existingBackendService.loadBalancingScheme = 'EXTERNAL'
      existingBackendService.protocol = description.ipProtocol
      backendServiceOp = safeRetry.doRetry(
        { timeExecute(
              compute.backendServices().update(project, existingBackendService.getName(), existingBackendService),
              "compute.backendServices.update",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Update',
        "Backend service $description.backendService.name",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    }
    if (backendServiceOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, backendServiceOp.getName(),
        null, task, "backend service " + backendServiceName, BASE_PHASE)
    }

    String targetProxyUrl = null
    if (!existingTargetProxy) {
      task.updateStatus BASE_PHASE, "Creating target ssl proxy ${targetProxyName}..."
      def targetProxy = new TargetSslProxy(
        name: targetProxyName,
        service: GCEUtil.buildBackendServiceUrl(project, backendServiceName),
        sslCertificates: [GCEUtil.buildCertificateUrl(project, description.certificate)]
      )
      Operation proxyOp = safeRetry.doRetry(
        { timeExecute(
              compute.targetSslProxies().insert(project, targetProxy),
              "compute.targetSslProxies.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Insert',
        "Target ssl proxy ${targetProxyName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      ) as Operation
      targetProxyUrl = proxyOp.getTargetLink()
      googleOperationPoller.waitForGlobalOperation(compute, project, proxyOp.getName(),
        null, task, "target ssl proxy " + targetProxyName, BASE_PHASE)
    } else if (existingTargetProxy && needToUpdateTargetProxy) {
      task.updateStatus BASE_PHASE, "Updating target ssl proxy ${targetProxyName}..."

      TargetSslProxiesSetSslCertificatesRequest certReq = new TargetSslProxiesSetSslCertificatesRequest()
      certReq.setSslCertificates([GCEUtil.buildCertificateUrl(project, description.certificate)])
      timeExecute(
          compute.targetSslProxies().setSslCertificates(project, existingTargetProxy.getName(), certReq),
          "compute.targetSllProxies.setSslCertificates",
          TAG_SCOPE, SCOPE_GLOBAL)

      TargetSslProxiesSetBackendServiceRequest bsReq = new TargetSslProxiesSetBackendServiceRequest()
      bsReq.setService(GCEUtil.buildBackendServiceUrl(project, backendServiceName))
      timeExecute(
            compute.targetSslProxies().setBackendService(project, existingTargetProxy.getName(), bsReq),
            "compute.targetSslProxies.setBackendService",
            TAG_SCOPE, SCOPE_GLOBAL)
    }

    if (!existingForwardingRule) {
      task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName..."
      def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        loadBalancingScheme: 'EXTERNAL',
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.certificate ? "443" : description.portRange,
        target: targetProxyUrl,
      )
      Operation ruleOp = safeRetry.doRetry(
        { timeExecute(
              compute.globalForwardingRules().insert(project, forwardingRule),
              "compute.globalForwardingRules.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        'Insert',
        "Global forwarding rule ${description.loadBalancerName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      ) as Operation
      googleOperationPoller.waitForGlobalOperation(compute, project, ruleOp.getName(),
        null, task, "forwarding rule " + description.loadBalancerName, BASE_PHASE)
    }

    // Delete extraneous listeners.
    description.listenersToDelete?.each { String forwardingRuleName ->
      task.updateStatus BASE_PHASE, "Deleting listener ${forwardingRuleName}..."
      GCEUtil.deleteGlobalListener(compute, project, forwardingRuleName, safeRetry)
    }

    task.updateStatus BASE_PHASE, "Done upserting load balancer $description.loadBalancerName."
    [loadBalancers: [('global'): [name: description.loadBalancerName]]]
  }
}
