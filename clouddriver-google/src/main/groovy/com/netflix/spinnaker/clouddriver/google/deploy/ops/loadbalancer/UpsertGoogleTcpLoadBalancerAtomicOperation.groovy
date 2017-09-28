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
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertGoogleTcpLoadBalancerAtomicOperation extends UpsertGoogleLoadBalancerAtomicOperation {
  private static final String BASE_PHASE = "UPSERT_TCP_LOAD_BALANCER"
  public static final String TARGET_TCP_PROXY_NAME_SUFFIX = "target-tcp-proxy"

  private final UpsertGoogleLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  UpsertGoogleTcpLoadBalancerAtomicOperation(UpsertGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "region": "global", "portRange": "443", "ipProtocol": "TCP", "credentials" : "my-account-name", "loadBalancerName" : "testlb", "backendService": {"name": "default-backend-service", "sessionAffinity": "NONE", "backends": [], "healthCheck": {"name": "basic-check", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1, "healthCheckType": "TCP"}}, "loadBalancerType": "TCP"}} ]' localhost:7002/gce/ops
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
    TargetTcpProxy existingTargetProxy
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
            "compute.globalForwardingRules.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Global forwarding rule ${description.loadBalancerName}",
      task,
      [400, 403, 412],
      [404],
      [action: "get", phase: BASE_PHASE, operation: "compute.globalForwardingRules.get", (TAG_SCOPE): SCOPE_GLOBAL],
      registry
    ) as ForwardingRule
    String targetProxyName = "${description.loadBalancerName}-${TARGET_TCP_PROXY_NAME_SUFFIX}"
    if (existingForwardingRule) {
      // Fetch the target proxy.
      targetProxyName = GCEUtil.getLocalName(existingForwardingRule.target)
      existingTargetProxy = safeRetry.doRetry(
        { timeExecute(
              compute.targetTcpProxies().get(project, targetProxyName),
              "compute.targetTcpProxies.get",
              TAG_SCOPE, SCOPE_GLOBAL)},
        "Target tcp proxy ${targetProxyName}",
        task,
        [400, 403, 412],
        [404],
        [action: "get", phase: BASE_PHASE, operation: "compute.targetTcpProxies.get", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      ) as TargetTcpProxy
    }

    if (existingTargetProxy) {
      needToUpdateTargetProxy = backendServiceName != GCEUtil.getLocalName(existingTargetProxy.getService())
    }

    existingBackendService = safeRetry.doRetry(
      { timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendServices.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Global backend service $backendServiceName",
      task,
      [400, 403, 412],
      [404],
      [action: "get", phase: BASE_PHASE, operation: "compute.backendServices.get", (TAG_SCOPE): SCOPE_GLOBAL],
      registry
    ) as BackendService
    if (existingBackendService) {
      Boolean differentHealthChecks = existingBackendService.getHealthChecks().collect { GCEUtil.getLocalName(it) } != [healthCheckName]
      Boolean differentPortName = existingBackendService.getPortName() != description.backendService.portName
      Boolean differentConnectionDraining = existingBackendService.getConnectionDraining()?.getDrainingTimeoutSec() != description.backendService.connectionDrainingTimeoutSec
      Boolean differentSessionAffinity = GoogleSessionAffinity.valueOf(existingBackendService.getSessionAffinity()) != description.backendService.sessionAffinity ||
        existingBackendService.getAffinityCookieTtlSec() != description.backendService.affinityCookieTtlSec
      needToUpdateBackendService = differentHealthChecks || differentPortName || differentSessionAffinity || differentConnectionDraining
    }

    // Note: TCP LBs only use HealthCheck objects, _not_ Http(s)HealthChecks. The actual check (i.e. Ssl, Tcp, Http(s))
    // is nested in a field inside the HealthCheck object.
    existingHealthCheck = safeRetry.doRetry(
      { timeExecute(
            compute.healthChecks().get(project, healthCheckName),
            "compute.healthChecks.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Health check $healthCheckName",
      task,
      [400, 403, 412],
      [404],
      [action: "get", phase: BASE_PHASE, operation: "compute.healthChecks.get", (TAG_SCOPE): SCOPE_GLOBAL],
      registry
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
        "Health check $healthCheckName",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "copmute.healthChecks.insert", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )
    } else if (existingHealthCheck && needToUpdateHealthCheck) {
      task.updateStatus BASE_PHASE, "Updating health check $healthCheckName..."
      GCEUtil.updateExistingHealthCheck(existingHealthCheck, descriptionHealthCheck)
      healthCheckOp = safeRetry.doRetry(
        { timeExecute(
              compute.healthChecks().update(project, healthCheckName, existingHealthCheck as HealthCheck),
              "compute.healthChecks.update",
              TAG_SCOPE, SCOPE_GLOBAL) },
        "Health check $healthCheckName",
        task,
        [400, 403, 412],
        [],
        [action: "update", phase: BASE_PHASE, operation: "compute.healthChecks.update", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
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
        portName: description.backendService.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME,
        connectionDraining: new ConnectionDraining().setDrainingTimeoutSec(description.backendService.connectionDrainingTimeoutSec),
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
        "Backend service $description.backendService.name",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "compute.backendServices.insert", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )
    } else if (existingBackendService && needToUpdateBackendService) {
      task.updateStatus BASE_PHASE, "Upating backend service ${description.backendService.name}..."
      existingBackendService.healthChecks = [GCEUtil.buildHealthCheckUrl(project, healthCheckName)]
      existingBackendService.sessionAffinity = description.backendService.sessionAffinity ?: 'NONE'
      existingBackendService.affinityCookieTtlSec = description.backendService.affinityCookieTtlSec
      existingBackendService.loadBalancingScheme = 'EXTERNAL'
      existingBackendService.protocol = description.ipProtocol
      existingBackendService.portName = description.backendService.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME
      existingBackendService.connectionDraining = new ConnectionDraining().setDrainingTimeoutSec(description.backendService.connectionDrainingTimeoutSec)
      backendServiceOp = safeRetry.doRetry(
        { timeExecute(
              compute.backendServices().update(project, existingBackendService.getName(), existingBackendService),
              "compute.backendServices.update",
              TAG_SCOPE, SCOPE_GLOBAL) },
        "Backend service $description.backendService.name",
        task,
        [400, 403, 412],
        [],
        [action: "update", phase: BASE_PHASE, operation: "compute.backendServices.update", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )
    }
    if (backendServiceOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, backendServiceOp.getName(),
        null, task, "backend service " + backendServiceName, BASE_PHASE)
    }

    String targetProxyUrl = null
    if (!existingTargetProxy) {
      task.updateStatus BASE_PHASE, "Creating target tcp proxy ${targetProxyName}..."
      def targetProxy = new TargetTcpProxy(
        name: targetProxyName,
        service: GCEUtil.buildBackendServiceUrl(project, backendServiceName)
      )
      Operation proxyOp = safeRetry.doRetry(
        { timeExecute(
              compute.targetTcpProxies().insert(project, targetProxy),
              "compute.targetTcpProxies.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        "Target tcp proxy ${targetProxyName}",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "compute.targetTcpProxies.insert", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      ) as Operation
      targetProxyUrl = proxyOp.getTargetLink()
      googleOperationPoller.waitForGlobalOperation(compute, project, proxyOp.getName(),
        null, task, "target tcp proxy " + targetProxyName, BASE_PHASE)
    } else if (existingTargetProxy && needToUpdateTargetProxy) {
      task.updateStatus BASE_PHASE, "Updating target tcp proxy ${targetProxyName}..."

      TargetTcpProxiesSetBackendServiceRequest bsReq = new TargetTcpProxiesSetBackendServiceRequest()
      bsReq.setService(GCEUtil.buildBackendServiceUrl(project, backendServiceName))
      timeExecute(
            compute.targetTcpProxies().setBackendService(project, existingTargetProxy.getName(), bsReq),
            "compute.targetTcpProxies.setBackendService",
            TAG_SCOPE, SCOPE_GLOBAL)
    }

    if (!existingForwardingRule) {
      task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName..."
      def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        loadBalancingScheme: 'EXTERNAL',
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.portRange,
        target: targetProxyUrl,
      )
      Operation ruleOp = safeRetry.doRetry(
        { timeExecute(
              compute.globalForwardingRules().insert(project, forwardingRule),
              "compute.globalForwardingRules.insert",
              TAG_SCOPE, SCOPE_GLOBAL) },
        "Global forwarding rule ${description.loadBalancerName}",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "compute.globalForwardingRules.insert", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      ) as Operation

      // Orca's orchestration for upserting a Google load balancer does not contain a task
      // to wait for the state of the platform to show that a load balancer was created (for good reason,
      // that would be a complicated operation). Instead, Orca waits for Clouddriver to execute this operation
      // and do a force cache refresh. We should wait for the whole load balancer to be created in the platform
      // before we exit this upsert operation, so we wait for the forwarding rule to be created before continuing
      // so we _know_ the state of the platform when we do a force cache refresh.
      googleOperationPoller.waitForGlobalOperation(compute, project, ruleOp.getName(),
        null, task, "forwarding rule " + description.loadBalancerName, BASE_PHASE)
    }

    // Delete extraneous listeners.
    description.listenersToDelete?.each { String forwardingRuleName ->
      task.updateStatus BASE_PHASE, "Deleting listener ${forwardingRuleName}..."
      GCEUtil.deleteGlobalListener(compute, project, forwardingRuleName, BASE_PHASE, safeRetry, this)
    }

    task.updateStatus BASE_PHASE, "Done upserting load balancer $description.loadBalancerName."
    [loadBalancers: [('global'): [name: description.loadBalancerName]]]
  }
}
