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
    boolean needToUpdateForwardingRule = false
    boolean needToUpdateTargetProxy = false
    boolean needToUpdateBackendService = false
    boolean needToUpdateHealthCheck = false

    // Check if there already exists a forwarding rule with the requested name.
    SafeRetry<ForwardingRule> ruleRetry = new SafeRetry<ForwardingRule>()
    existingForwardingRule = ruleRetry.doRetry(
      { compute.globalForwardingRules().get(project, description.loadBalancerName).execute() },
      'Get',
      "Global forwarding rule ${description.loadBalancerName}",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    )
    String targetProxyName = "${description.loadBalancerName}-${TARGET_SSL_PROXY_NAME_SUFFIX}"
    if (existingForwardingRule) {
      needToUpdateForwardingRule = (description.ipAddress ? existingForwardingRule.getIPAddress() != description.ipAddress : false) ||
        (description.ipProtocol ? existingForwardingRule.getIPProtocol() != description.ipProtocol : false) ||
        (existingForwardingRule.getPortRange() != "${description.portRange}-${description.portRange}")

      // If a forwarding rule exists, fetch the target proxy as well.
      targetProxyName = GCEUtil.getLocalName(existingForwardingRule.target)
      SafeRetry<TargetSslProxy> proxyRetry = new SafeRetry<TargetSslProxy>()
      existingTargetProxy = proxyRetry.doRetry(
        { compute.targetSslProxies().get(project, targetProxyName).execute() },
        'Get',
        "Target ssl proxy ${targetProxyName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        [404]
      )
    }

    if (existingTargetProxy) {
      needToUpdateTargetProxy = backendServiceName != GCEUtil.getLocalName(existingTargetProxy.getService()) ||
        description.certificate != GCEUtil.getLocalName(existingTargetProxy.getSslCertificates()[0])
    }

    SafeRetry<BackendService> serviceRetry = new SafeRetry<BackendService>()
    existingBackendService = serviceRetry.doRetry(
      { compute.backendServices().get(project, backendServiceName).execute() },
      'Get',
      "Global backend service $backendServiceName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    )
    if (existingBackendService) {
      Boolean differentHealthChecks = existingBackendService.getHealthChecks().collect { GCEUtil.getLocalName(it) } != [healthCheckName]
      Boolean differentSessionAffinity = GoogleSessionAffinity.valueOf(existingBackendService.getSessionAffinity()) != description.backendService.sessionAffinity ||
        existingBackendService.getAffinityCookieTtlSec() != description.backendService.affinityCookieTtlSec
      needToUpdateBackendService = differentHealthChecks || differentSessionAffinity
    }

    // Note: SSL LBs only use HealthCheck objects, _not_ Http(s)HealthChecks. The actual check (i.e. Ssl, Tcp, Http(s))
    // is nested in a field inside the HealthCheck object.
    SafeRetry<HealthCheck> hcSafeRetry = new SafeRetry<HealthCheck>()
    existingHealthCheck = hcSafeRetry.doRetry(
      { compute.healthChecks().get(project, healthCheckName).execute() },
      'Get',
      "Health check $healthCheckName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      [404]
    )

    needToUpdateHealthCheck = existingHealthCheck && GCEUtil.healthCheckShouldBeUpdated(existingHealthCheck, descriptionHealthCheck)

    // Now we start phase 2 of our plan -- upsert all the components.
    def healthCheckOp = null
    if (!existingHealthCheck) {
      task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."
      def newHealthCheck = GCEUtil.createNewHealthCheck(descriptionHealthCheck)
      SafeRetry<Operation> insertRetry = new SafeRetry<Operation>()
      healthCheckOp = insertRetry.doRetry(
        { compute.healthChecks().insert(project, newHealthCheck as HealthCheck).execute() },
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
      SafeRetry<Operation> updateRetry = new SafeRetry<Operation>()
      healthCheckOp = updateRetry.doRetry(
        { compute.healthChecks().update(project, healthCheckName, existingHealthCheck as HealthCheck).execute() },
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
    SafeRetry<Operation> bsRetry = new SafeRetry<Operation>()
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
      backendServiceOp = bsRetry.doRetry(
        { compute.backendServices().insert(project, bs).execute() },
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
      backendServiceOp = bsRetry.doRetry(
        { compute.backendServices().update(project, existingBackendService.getName(), existingBackendService).execute() },
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

    SafeRetry<Operation> proxyRetry = new SafeRetry<Operation>()
    String targetProxyUrl = null
    Operation proxyOp
    if (!existingTargetProxy) {
      task.updateStatus BASE_PHASE, "Creating target ssl proxy ${targetProxyName}..."
      def targetProxy = new TargetSslProxy(
        name: targetProxyName,
        service: GCEUtil.buildBackendServiceUrl(project, backendServiceName),
        sslCertificates: [GCEUtil.buildCertificateUrl(project, description.certificate)]
      )
      proxyOp = proxyRetry.doRetry(
        { compute.targetSslProxies().insert(project, targetProxy).execute() },
        'Insert',
        "Target ssl proxy ${targetProxyName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    } else if (existingTargetProxy && needToUpdateTargetProxy) {
      task.updateStatus BASE_PHASE, "Updating target ssl proxy ${targetProxyName}..."
      // We have to delete the previous listener and recreate it...
      // Delete old. NOTE: The forwarding rule is also deleted here. We recreate it later in this case.
      def deleteOp = GCEUtil.deleteGlobalListener(compute, project, description.loadBalancerName)
      // Wait...
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteOp.getName(),
        null, task, "target ssl proxy " + targetProxyName, BASE_PHASE)
      // Insert after setting attributes.
      existingTargetProxy.sslCertificates = [GCEUtil.buildCertificateUrl(project, description.certificate)]
      existingTargetProxy.service = GCEUtil.buildBackendServiceUrl(project, backendServiceName)
      proxyOp = proxyRetry.doRetry(
        { compute.targetSslProxies().insert(project, existingTargetProxy).execute() },
        'Insert',
        "Target ssl proxy ${targetProxyName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    }
    if (proxyOp) {
      targetProxyUrl = proxyOp.getTargetLink()
      googleOperationPoller.waitForGlobalOperation(compute, project, proxyOp.getName(),
        null, task, "target ssl proxy " + targetProxyName, BASE_PHASE)
    }

    SafeRetry<Operation> ruleOpRetry = new SafeRetry<Operation>()
    Operation ruleOp
    if (!existingForwardingRule || (existingTargetProxy && needToUpdateTargetProxy)) {
      // NOTE: if we needed to update the proxy, the forwarding rule is also deleted so we should recreate it.
      // This situation occurs because there's no way to update the TargetSslProxy in place, so we have to
      // destroy and recreate the forwarding rule pointing to the target proxy as well.
      task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName..."
      def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        loadBalancingScheme: 'EXTERNAL',
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.certificate ? "443" : description.portRange,
        target: targetProxyUrl,
      )
      ruleOp = ruleOpRetry.doRetry(
        { compute.globalForwardingRules().insert(project, forwardingRule).execute() },
        'Insert',
        "Global forwarding rule ${description.loadBalancerName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    } else if (existingForwardingRule && needToUpdateForwardingRule) {
      task.updateStatus BASE_PHASE, "Updating forwarding rule $description.loadBalancerName..."
      def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        loadBalancingScheme: 'EXTERNAL',
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.certificate ? "443" : description.portRange,
        target: targetProxyUrl,
      )
      def deleteRuleOp = ruleOpRetry.doRetry(
        { compute.globalForwardingRules().delete(project, description.loadBalancerName).execute() },
        'Delete',
        "Global forwarding rule ${description.loadBalancerName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        [404]
      )
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteRuleOp.getName(),
        null, task, "forwarding rule " + description.loadBalancerName, BASE_PHASE)
      ruleOpRetry.doRetry(
        { compute.globalForwardingRules().insert(project, forwardingRule).execute() },
        'Insert',
        "Global forwarding rule ${description.loadBalancerName}",
        task,
        BASE_PHASE,
        [400, 403, 412],
        []
      )
    }
    if (ruleOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, ruleOp.getName(),
        null, task, "forwarding rule " + description.loadBalancerName, BASE_PHASE)
    }

    // Delete extraneous listeners.
    description.listenersToDelete?.each { String forwardingRuleName ->
      task.updateStatus BASE_PHASE, "Deleting listener ${forwardingRuleName}..."
      GCEUtil.deleteGlobalListener(compute, project, forwardingRuleName)
    }

    task.updateStatus BASE_PHASE, "Done upserting load balancer $description.loadBalancerName."
    [loadBalancers: [('global'): [name: description.loadBalancerName]]]
  }
}
