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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
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
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DeleteGoogleInternalLoadBalancerAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_INTERNAL_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  private DeleteGoogleLoadBalancerDescription description

  @VisibleForTesting
  GoogleOperationPoller.ThreadSleeper threadSleeper = new GoogleOperationPoller.ThreadSleeper()

  DeleteGoogleInternalLoadBalancerAtomicOperation() {}

  DeleteGoogleInternalLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "region": "us-central1", "credentials": "my-account-name", "loadBalancerName": "testlb", "loadBalancerType": "INTERNAL"}} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName " +
      "in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region
    def forwardingRuleName = description.loadBalancerName

    task.updateStatus BASE_PHASE, "Retrieving forwarding rule $forwardingRuleName in $region..."

    // NOTE: get all the forwarding rule names to resolve which ones to delete later.
    List<ForwardingRule> projectForwardingRules = timeExecute(
        compute.forwardingRules().list(project, region),
        "compute.forwardingRules.list",
        TAG_SCOPE, SCOPE_GLOBAL).getItems()

    ForwardingRule forwardingRule = projectForwardingRules.find { it.name == forwardingRuleName }
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Forwarding rule $forwardingRuleName not found in $region for $project",
        task, BASE_PHASE)
    }

    def backendServiceName = GCEUtil.getLocalName(forwardingRule.backendService)

    // Determine which listeners to delete.
    List<String> listenersToDelete = []
    projectForwardingRules.each { ForwardingRule rule ->
      try {
        if (GCEUtil.getLocalName(rule.getBackendService()) == backendServiceName) {
          listenersToDelete << rule.getName()
        }
      } catch (GoogleJsonResponseException e) {
        // 404 is thrown if the target proxy does not exist.
        // We can ignore 404's here because we are iterating over all forwarding rules and some other process may have
        // deleted the target proxy between the time we queried for the list of forwarding rules and now.
        // Any other exception needs to be propagated.
        if (e.getStatusCode() != 404) {
          throw e
        }
      }
    }

    task.updateStatus BASE_PHASE, "Retrieving backend service $backendServiceName in $region..."

    BackendService backendService = safeRetry.doRetry(
      { timeExecute(
            compute.regionBackendServices().get(project, region, backendServiceName),
            "compute.regionBackendServices.get",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Region backend service $backendServiceName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: "BASE_PHASE", operation: "compute.regionBackendServices.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as BackendService
    if (backendService == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Backend service $backendServiceName not found in $region for $project",
        task, BASE_PHASE)
    }

    def healthCheckUrl = backendService.healthChecks[0]
    def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
    def healthCheckType = Utils.getHealthCheckType(healthCheckUrl)
    def healthCheckGet = null
    def operationName = null
    switch (healthCheckType) {
      case "httpHealthChecks":
        operationName = "compute.httpHealthChecks.get"
        healthCheckGet = {
            timeExecute(
                compute.httpHealthChecks().get(project, healthCheckName),
                "compute.httpHealthChecks.get",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      case "httpsHealthChecks":
        operationName = "copmute.httpsHealthChecks.get"
        healthCheckGet = {
            timeExecute(
                compute.httpsHealthChecks().get(project, healthCheckName),
                "compute.httpsHealthChecks.get",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      case "healthChecks":
        operationName = "compute.healthChecks.get"
        healthCheckGet = {
            timeExecute(
                compute.healthChecks().get(project, healthCheckName),
                "compute.healthChecks.get",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      default:
        throw new IllegalStateException("Unknown health check type for health check named: ${healthCheckName}.")
        break
    }

    def healthCheck = safeRetry.doRetry(
      healthCheckGet,
      "Health check $healthCheckName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: operationName, (TAG_SCOPE): SCOPE_GLOBAL],
      registry
    )
    if (healthCheck == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Health check $healthCheckName not found for $project",
        task, BASE_PHASE)
    }

    // Now delete all the components, waiting for each delete operation to finish.
    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    listenersToDelete.each { String ruleName ->
      task.updateStatus BASE_PHASE, "Deleting listener $ruleName..."

      Operation deleteForwardingRuleOp = safeRetry.doRetry(
          {
            timeExecute(
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
            timeoutSeconds, task, "Regional forwarding rule $forwardingRuleName", BASE_PHASE)
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

    Closure<Operation> deleteHealthCheckClosure = null
    switch (healthCheckType) {
      case "httpHealthChecks":
        deleteHealthCheckClosure = {
            timeExecute(
                compute.httpHealthChecks().delete(project, healthCheckName),
                "compute.httpHealthChecks.delete",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      case "httpsHealthChecks":
        deleteHealthCheckClosure = {
            timeExecute(
                compute.httpsHealthChecks().delete(project, healthCheckName),
                "compute.httpsHealthChecks.delete",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      case "healthChecks":
        deleteHealthCheckClosure = {
            timeExecute(
                compute.healthChecks().delete(project, healthCheckName),
                "compute.healthChecks.delete",
                TAG_SCOPE, SCOPE_GLOBAL) }
        break
      default:
        log.warn("Unknown health check type for health check named: ${healthCheckName}.")
        break
    }
    Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
      deleteHealthCheckClosure,
      "Health check $healthCheckName",
      project,
      task,
      [action: 'delete', operation: 'compute.' + healthCheckType + '.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_GLOBAL],
      safeRetry,
      this
    )
    if (deleteHealthCheckOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteHealthCheckOp.getName(),
        timeoutSeconds, task, "Health check $healthCheckName", BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting internal load balancer $description.loadBalancerName in $region."
    null
  }
}
