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
import com.google.api.services.compute.model.Operation
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleAtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

/**
 * Deletes the resource graph for a regional external passthrough Network Load Balancer.
 *
 * <p>Only the EXTERNAL passthrough shape is owned here: regional forwarding rule, regional backend
 * service, and optionally the regional health check when the request allows health-check cleanup.
 */
@Slf4j
class DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER"

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  private DeleteGoogleLoadBalancerDescription description

  @VisibleForTesting
  GoogleOperationPoller.ThreadSleeper threadSleeper = new GoogleOperationPoller.ThreadSleeper()

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperation() {}

  DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region
    def forwardingRuleName = description.loadBalancerName

    List<ForwardingRule> projectForwardingRules = timeExecute(
      compute.forwardingRules().list(project, region),
      "compute.forwardingRules.list",
      TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).getItems() ?: []

    // Same-name regional forwarding rules can represent other LB families. Require the exact
    // EXTERNAL passthrough shape before deleting any listener or shared backend-service graph.
    ForwardingRule forwardingRule = projectForwardingRules.find {
      it.name == forwardingRuleName && GCEUtil.isRegionalExternalNetworkPassthroughForwardingRule(it)
    }
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Regional external network forwarding rule $forwardingRuleName not found in $region for $project",
        task, BASE_PHASE)
    }

    def backendServiceName = GCEUtil.getLocalName(forwardingRule.backendService)
    List<String> listenersToDelete = projectForwardingRules.findAll { ForwardingRule rule ->
      GCEUtil.isRegionalExternalNetworkPassthroughForwardingRule(rule) &&
        GCEUtil.getLocalName(rule.backendService) == backendServiceName
    }.collect { it.name }

    BackendService backendService = safeRetry.doRetry(
      { timeExecute(
        compute.regionBackendServices().get(project, region, backendServiceName),
        "compute.regionBackendServices.get",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Region backend service $backendServiceName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: "compute.regionBackendServices.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as BackendService
    // Recheck the backend service scheme before deleting to avoid mutating a same-named INTERNAL
    // passthrough backend service if the forwarding-rule graph was malformed.
    if (backendService == null || backendService.loadBalancingScheme != "EXTERNAL") {
      GCEUtil.updateStatusAndThrowNotFoundException("External regional backend service $backendServiceName not found in $region for $project",
        task, BASE_PHASE)
    }

    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    listenersToDelete.each { String ruleName ->
      task.updateStatus BASE_PHASE, "Deleting listener $ruleName..."
      Operation deleteForwardingRuleOp = safeRetry.doRetry(
        { timeExecute(
          compute.forwardingRules().delete(project, region, ruleName),
          "compute.forwardingRules.delete",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional forwarding rule $ruleName",
        task,
        [400, 412],
        [404],
        [action: "delete", phase: BASE_PHASE, operation: "compute.forwardingRules.delete", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        registry
      ) as Operation

      if (deleteForwardingRuleOp) {
        googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOp.getName(),
          timeoutSeconds, task, "Regional forwarding rule $ruleName", BASE_PHASE)
      }
    }

    Operation deleteBackendServiceOp = GCEUtil.deleteIfNotInUse(
      { timeExecute(
        compute.regionBackendServices().delete(project, region, backendServiceName),
        "compute.regionBackendServices.delete",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Region backend service $backendServiceName",
      project,
      task,
      [action: 'delete', operation: 'compute.regionBackendServices.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      safeRetry,
      this
    )
    if (deleteBackendServiceOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteBackendServiceOp.getName(),
        timeoutSeconds, task, "Regional backend service $backendServiceName", BASE_PHASE)
    }

    if (description.deleteHealthChecks) {
      def healthCheckUrl = backendService.healthChecks?.getAt(0)
      if (!healthCheckUrl) {
        GCEUtil.updateStatusAndThrowNotFoundException("External regional backend service $backendServiceName has no health check to delete in $region for $project",
          task, BASE_PHASE)
      }
      def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
      Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
        { timeExecute(
          compute.regionHealthChecks().delete(project, region, healthCheckName),
          "compute.regionHealthChecks.delete",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
        "Regional health check $healthCheckName",
        project,
        task,
        [action: 'delete', operation: 'compute.regionHealthChecks.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
        safeRetry,
        this
      )
      if (deleteHealthCheckOp) {
        googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteHealthCheckOp.getName(),
          timeoutSeconds, task, "Regional health check $healthCheckName", BASE_PHASE)
      }
    }

    task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName in $region."
    null
  }
}
