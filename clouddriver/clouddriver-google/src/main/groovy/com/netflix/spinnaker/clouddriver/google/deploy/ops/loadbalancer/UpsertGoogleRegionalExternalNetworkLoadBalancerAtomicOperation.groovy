/*
 * Copyright 2026 Harness, Inc.
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

import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import org.springframework.beans.factory.annotation.Autowired

/**
 * Creates or updates a regional external passthrough Network Load Balancer.
 *
 * <p>GCP models this as a regional forwarding rule with {@code loadBalancingScheme=EXTERNAL}, no
 * target proxy, and a direct regional backend service for TCP/UDP traffic.
 */
class UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperation extends GoogleAtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER"

  @Autowired
  SafeRetry safeRetry

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private final UpsertGoogleLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperation() {}

  UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperation(UpsertGoogleLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.loadBalancerName in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region
    GoogleHealthCheck descriptionHealthCheck = description.backendService.healthCheck
    String backendServiceName = description.backendService.name
    String healthCheckName = descriptionHealthCheck.name

    description.ipProtocol = description.ipProtocol ?: Constants.DEFAULT_IP_PROTOCOL

    ForwardingRule existingForwardingRule = GCEUtil.queryRegionalForwardingRule(project, description.loadBalancerName, compute, task, BASE_PHASE, this)
    BackendService existingBackendService
    HealthCheck existingHealthCheck

    boolean needToUpdateForwardingRule = false
    boolean needToUpdateBackendService = false
    boolean needToUpdateHealthCheck = false

    if (existingForwardingRule && (description.region != GCEUtil.getLocalName(existingForwardingRule.region))) {
      throw new GoogleOperationException("There is already a load balancer named " +
        "$description.loadBalancerName (in region ${GCEUtil.getLocalName(existingForwardingRule.region)}). " +
        "Please specify a different name.")
    } else if (existingForwardingRule && (description.region == GCEUtil.getLocalName(existingForwardingRule.region))) {
      // Same-name regional forwarding rules can belong to other LB families. Only mutate the
      // EXTERNAL passthrough shape owned by this operation.
      if (!GCEUtil.isRegionalExternalNetworkPassthroughForwardingRule(existingForwardingRule)) {
        throw new GoogleOperationException("There is already a non-regional-external-network load balancer named $description.loadBalancerName in $description.region.")
      }
      // Treat omitted IP/tier as "preserve current value" so edits do not churn static/ephemeral
      // address assignment or network tier unless the caller explicitly changes them.
      needToUpdateForwardingRule = description.ports != existingForwardingRule.getPorts() ||
        (description.ipAddress && description.ipAddress != existingForwardingRule.getIPAddress()) ||
        (description.networkTier && description.networkTier != existingForwardingRule.getNetworkTier())
    }

    existingBackendService = safeRetry.doRetry(
      { timeExecute(
        compute.regionBackendServices().get(project, region, backendServiceName),
        "compute.regionBackendServices.get",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Region backend service $backendServiceName",
      task,
      [400, 403, 412],
      [404],
      [action: "get", phase: BASE_PHASE, operation: "compute.regionBackendServices.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as BackendService
    if (existingBackendService) {
      if (existingBackendService.loadBalancingScheme != "EXTERNAL") {
        throw new GoogleOperationException("Backend service $backendServiceName is not an EXTERNAL regional backend service.")
      }
      Boolean differentHealthChecks = existingBackendService.getHealthChecks().collect { GCEUtil.getLocalName(it) } != [healthCheckName]
      // GCP may omit sessionAffinity for the default; normalize null to NONE before comparing.
      GoogleSessionAffinity existingSessionAffinity = existingBackendService.getSessionAffinity() ?
        GoogleSessionAffinity.valueOf(existingBackendService.getSessionAffinity()) : null
      GoogleSessionAffinity desiredSessionAffinity = description.backendService.sessionAffinity ?: GoogleSessionAffinity.NONE
      Boolean differentSessionAffinity = (existingSessionAffinity ?: GoogleSessionAffinity.NONE) != desiredSessionAffinity
      if (differentHealthChecks || differentSessionAffinity || existingBackendService.protocol != description.ipProtocol) {
        needToUpdateBackendService = true
      }
    }

    existingHealthCheck = safeRetry.doRetry(
      { timeExecute(
        compute.regionHealthChecks().get(project, region, healthCheckName),
        "compute.regionHealthChecks.get",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Regional health check $healthCheckName",
      task,
      [400, 403, 412],
      [404],
      [action: "get", phase: BASE_PHASE, operation: "compute.regionHealthChecks.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as HealthCheck

    needToUpdateHealthCheck = existingHealthCheck && GCEUtil.healthCheckShouldBeUpdated(existingHealthCheck, descriptionHealthCheck)

    def healthCheckOp = null
    if (!existingHealthCheck) {
      task.updateStatus BASE_PHASE, "Creating regional health check $healthCheckName..."
      def newHealthCheck = GCEUtil.createNewHealthCheck(descriptionHealthCheck)
      healthCheckOp = safeRetry.doRetry(
        { timeExecute(
          compute.regionHealthChecks().insert(project, region, newHealthCheck as HealthCheck),
          "compute.regionHealthChecks.insert",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional health check $healthCheckName",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "compute.regionHealthChecks.insert", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        registry
      )
    } else if (needToUpdateHealthCheck) {
      task.updateStatus BASE_PHASE, "Updating regional health check $healthCheckName..."
      GCEUtil.updateExistingHealthCheck(existingHealthCheck, descriptionHealthCheck)
      healthCheckOp = safeRetry.doRetry(
        { timeExecute(
          compute.regionHealthChecks().update(project, region, healthCheckName, existingHealthCheck as HealthCheck),
          "compute.regionHealthChecks.update",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional health check $healthCheckName",
        task,
        [400, 403, 412],
        [],
        [action: "update", phase: BASE_PHASE, operation: "compute.regionHealthChecks.update", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        registry
      )
    }
    if (healthCheckOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, healthCheckOp.getName(),
        null, task, "regional health check " + healthCheckName, BASE_PHASE)
    }

    def backendServiceOp = null
    if (!existingBackendService) {
      task.updateStatus BASE_PHASE, "Creating regional external backend service ${description.backendService.name}..."
      BackendService bs = new BackendService(
        name: backendServiceName,
        healthChecks: [GCEUtil.buildRegionalHealthCheckUrl(project, region, healthCheckName)],
        sessionAffinity: description.backendService.sessionAffinity ?: 'NONE',
        loadBalancingScheme: 'EXTERNAL',
        protocol: description.ipProtocol
      )
      backendServiceOp = safeRetry.doRetry(
        { timeExecute(
          compute.regionBackendServices().insert(project, region, bs),
          "compute.regionBackendServices.insert",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional backend service $description.backendService.name",
        task,
        [400, 403, 412],
        [],
        [action: "insert", phase: BASE_PHASE, operation: "compute.regionBackendServices.insert", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        registry
      )
    } else if (needToUpdateBackendService) {
      task.updateStatus BASE_PHASE, "Updating regional external backend service ${description.backendService.name}..."
      existingBackendService.healthChecks = [GCEUtil.buildRegionalHealthCheckUrl(project, region, healthCheckName)]
      existingBackendService.sessionAffinity = description.backendService.sessionAffinity ?: 'NONE'
      existingBackendService.loadBalancingScheme = 'EXTERNAL'
      existingBackendService.protocol = description.ipProtocol
      backendServiceOp = safeRetry.doRetry(
        { timeExecute(
          compute.regionBackendServices().update(project, region, existingBackendService.getName(), existingBackendService),
          "compute.regionBackendServices.update",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional backend service $description.backendService.name",
        task,
        [400, 403, 412],
        [],
        [action: "update", phase: BASE_PHASE, operation: "compute.regionBackendServices.update", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        registry
      )
    }
    if (backendServiceOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, backendServiceOp.getName(),
        null, task, "regional backend service " + backendServiceName, BASE_PHASE)
    }

    if (!existingForwardingRule) {
      insertRegionalForwardingRule(compute, project, region, buildForwardingRule(project, region, null))
    } else if (needToUpdateForwardingRule) {
      deleteRegionalForwardingRule(compute, project, region, existingForwardingRule.getName())
      insertRegionalForwardingRule(compute, project, region, buildForwardingRule(project, region, existingForwardingRule))
    }

    description.listenersToDelete?.each { String forwardingRuleName ->
      deleteRegionalForwardingRule(compute, project, region, forwardingRuleName)
    }

    task.updateStatus BASE_PHASE, "Done upserting load balancer $description.loadBalancerName in $region."
    return [loadBalancers: [(region): [name: description.loadBalancerName]]]
  }

  private ForwardingRule buildForwardingRule(String project, String region, ForwardingRule existingForwardingRule) {
    // Updating ports requires forwarding-rule recreation. Preserve IP/tier from the deleted rule
    // when the request omits them; ephemeral IP preservation remains best-effort on GCP's side.
    new ForwardingRule(
      name: description.loadBalancerName,
      loadBalancingScheme: 'EXTERNAL',
      backendService: GCEUtil.buildRegionBackendServiceUrl(project, region, description.backendService.name),
      IPProtocol: description.ipProtocol,
      IPAddress: description.ipAddress ?: existingForwardingRule?.IPAddress,
      ports: description.ports,
      networkTier: description.networkTier ?: existingForwardingRule?.networkTier
    )
  }

  private void deleteRegionalForwardingRule(compute, String project, String region, String forwardingRuleName) {
    task.updateStatus BASE_PHASE, "Deleting listener ${forwardingRuleName}..."
    Operation deleteForwardingRuleOp = safeRetry.doRetry(
      { timeExecute(
        compute.forwardingRules().delete(project, region, forwardingRuleName),
        "compute.forwardingRules.delete",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Regional forwarding rule $forwardingRuleName",
      task,
      [400, 412],
      [404],
      [action: "delete", phase: BASE_PHASE, operation: "compute.forwardingRules.delete", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as Operation

    if (deleteForwardingRuleOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOp.getName(),
        30, task, "Regional forwarding rule $forwardingRuleName", BASE_PHASE)
    }
  }

  private void insertRegionalForwardingRule(compute, String project, String region, forwardingRule) {
    task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName..."
    Operation forwardingRuleOp = safeRetry.doRetry(
      { timeExecute(
        compute.forwardingRules().insert(project, region, forwardingRule),
        "compute.forwardingRules.insert",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Regional forwarding rule ${description.loadBalancerName}",
      task,
      [400, 403, 412],
      [],
      [action: "insert", phase: BASE_PHASE, operation: "compute.forwardingRules.insert", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as Operation

    googleOperationPoller.waitForRegionalOperation(compute, project, region, forwardingRuleOp.getName(),
      null, task, "forwarding rule " + description.loadBalancerName, BASE_PHASE)
  }
}
