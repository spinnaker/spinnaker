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
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DeleteGoogleSslLoadBalancerAtomicOperation extends DeleteGoogleLoadBalancerAtomicOperation {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  private DeleteGoogleLoadBalancerDescription description

  DeleteGoogleSslLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "credentials": "my-account-name", "loadBalancerName": "spin-lb", "deleteHealthChecks": true, "loadBalancerType": "SSL"}} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of ssl load balancer $description.loadBalancerName..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def forwardingRuleName = description.loadBalancerName

    // First we look everything up. Then, we call delete on all of it. Finally, we wait (with timeout) for all to complete.
    // Start with the forwarding rule.
    task.updateStatus BASE_PHASE, "Retrieving global forwarding rule $forwardingRuleName..."

    List<ForwardingRule> projectForwardingRules = timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules.list",
        TAG_SCOPE, SCOPE_GLOBAL).getItems()

    ForwardingRule forwardingRule = projectForwardingRules.find { it.name == forwardingRuleName }
    if (!forwardingRule) {
      GCEUtil.updateStatusAndThrowNotFoundException("Global forwarding rule $forwardingRuleName not found for $project",
        task, BASE_PHASE)
    }

    String targetProxyName = GCEUtil.getLocalName(forwardingRule.getTarget())
    // Target SSL proxy.
    task.updateStatus BASE_PHASE, "Retrieving target proxy $targetProxyName..."

    TargetSslProxy retrievedTargetProxy = GCEUtil.getTargetProxyFromRule(compute, project, forwardingRule, BASE_PHASE, safeRetry, this) as TargetSslProxy

    if (!retrievedTargetProxy) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target proxy $targetProxyName not found for $project", task,
        BASE_PHASE)
    }
    def backendServiceName = GCEUtil.getLocalName(retrievedTargetProxy.getService())

    List<String> listenersToDelete = []
    projectForwardingRules.each { ForwardingRule rule ->
      try {
        def proxy = GCEUtil.getTargetProxyFromRule(compute, project, rule, BASE_PHASE, safeRetry, this)
        if (GCEUtil.getLocalName(proxy?.service) == backendServiceName) {
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

    // Backend service.
    task.updateStatus BASE_PHASE, "Retrieving backend service $backendServiceName..."
    BackendService retrievedBackendService = safeRetry.doRetry(
      { timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendServices.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Backend service $backendServiceName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: "compute.backendServices.get", (TAG_SCOPE): SCOPE_GLOBAL],
      registry
    ) as BackendService
    if (!retrievedBackendService) {
      GCEUtil.updateStatusAndThrowNotFoundException("Backend service $backendServiceName not found for $project",
        task, BASE_PHASE)
    }
    if (retrievedBackendService?.backends) {
      task.updateStatus BASE_PHASE, "Server groups still associated with ssl load balancer ${description.loadBalancerName}. Failing..."
      throw new IllegalStateException("Server groups still associated with ssl load balancer: ${description.loadBalancerName}.")
    }

    def healthCheckName = Utils.getLocalName(retrievedBackendService.getHealthChecks()[0])
    HealthCheck retrievedHealthCheck = safeRetry.doRetry(
      { timeExecute(
            compute.healthChecks().get(project, healthCheckName),
            "compute.healthChecks.get",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Health check $healthCheckName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: "compute.healthChecks.get", (TAG_SCOPE): SCOPE_GLOBAL],
      getRegistry()
    ) as HealthCheck
    if (!retrievedHealthCheck) {
      GCEUtil.updateStatusAndThrowNotFoundException("Health check $healthCheckName not found for $project",
        task,
        BASE_PHASE)
    }

    // Delete all the components.
    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    listenersToDelete.each { String ruleName ->
      task.updateStatus BASE_PHASE, "Deleting listener $ruleName..."
      Operation operation = GCEUtil.deleteGlobalListener(compute, project, ruleName, BASE_PHASE, safeRetry, this)
      googleOperationPoller.waitForGlobalOperation(compute, project, operation.getName(),
        timeoutSeconds, task, "listener " + ruleName, BASE_PHASE)
    }

    Operation deleteBackendServiceOp = GCEUtil.deleteIfNotInUse(
      { timeExecute(
            compute.backendServices().delete(project, backendServiceName),
            "compute.backendServices.delete",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Backend service $backendServiceName",
      project,
      task,
      [action: 'delete', operation: 'compute.backendServices.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_GLOBAL],
      safeRetry,
      this
    )
    googleOperationPoller.waitForGlobalOperation(compute, project, deleteBackendServiceOp.getName(),
      timeoutSeconds, task, "backend service $backendServiceName", BASE_PHASE)

    if (description.deleteHealthChecks) {
      Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
        { timeExecute(
              compute.healthChecks().delete(project, healthCheckName),
              "compute.healthChecks.delete",
              TAG_SCOPE, SCOPE_GLOBAL) },
        "Health check $healthCheckName",
        project,
        task,
        [action: 'delete', operation: 'compute.healthChecks.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_GLOBAL],
        safeRetry,
        this
      )
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteHealthCheckOp.getName(),
        timeoutSeconds, task, "health check $healthCheckName", BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting ssl load balancer $description.loadBalancerName."
    null
  }
}
