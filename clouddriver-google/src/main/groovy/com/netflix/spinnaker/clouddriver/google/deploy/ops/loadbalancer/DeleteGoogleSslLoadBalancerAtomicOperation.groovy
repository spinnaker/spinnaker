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
    // Start with the forwaring rule.
    task.updateStatus BASE_PHASE, "Retrieving global forwarding rule $forwardingRuleName..."

    List<ForwardingRule> projectForwardingRules = compute.globalForwardingRules().list(project).execute().getItems()

    ForwardingRule forwardingRule = projectForwardingRules.find { it.name == forwardingRuleName }
    if (!forwardingRule) {
      GCEUtil.updateStatusAndThrowNotFoundException("Global forwarding rule $forwardingRuleName not found for $project",
        task, BASE_PHASE)
    }

    String targetProxyName = GCEUtil.getLocalName(forwardingRule.getTarget())
    // Target HTTP(S) proxy.
    task.updateStatus BASE_PHASE, "Retrieving target proxy $targetProxyName..."

    TargetSslProxy retrievedTargetProxy = GCEUtil.getTargetProxyFromRule(compute, project, forwardingRule) as TargetSslProxy

    if (!retrievedTargetProxy) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target proxy $targetProxyName not found for $project", task,
        BASE_PHASE)
    }
    def backendServiceName = GCEUtil.getLocalName(retrievedTargetProxy.getService())

    List<String> listenersToDelete = []
    projectForwardingRules.each { ForwardingRule rule ->
      def proxy = GCEUtil.getTargetProxyFromRule(compute, project, rule)
      if (GCEUtil.getLocalName(proxy?.service) == backendServiceName) {
        listenersToDelete << rule.getName()
      }
    }

    // Backend service.
    task.updateStatus BASE_PHASE, "Retrieving backend service $backendServiceName..."
    SafeRetry<BackendService> bsRetry = new SafeRetry<BackendService>()
    BackendService retrievedBackendService = bsRetry.doRetry(
      { compute.backendServices().get(project, backendServiceName).execute() },
      'Get',
      "Backend service $backendServiceName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      []
    )
    if (!retrievedBackendService) {
      GCEUtil.updateStatusAndThrowNotFoundException("Backend service $backendServiceName not found for $project",
        task, BASE_PHASE)
    }
    if (retrievedBackendService?.backends) {
      task.updateStatus BASE_PHASE, "Server groups still associated with ssl load balancer ${description.loadBalancerName}. Failing..."
      throw new IllegalStateException("Server groups still associated with ssl load balancer: ${description.loadBalancerName}.")
    }

    def healthCheckName = Utils.getLocalName(retrievedBackendService.getHealthChecks()[0])
    SafeRetry<HealthCheck> hcRetry = new SafeRetry<HealthCheck>()
    HealthCheck retrievedHealthCheck = hcRetry.doRetry(
      { compute.healthChecks().get(project, healthCheckName).execute() },
      'Get',
      "Health check $healthCheckName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      []
    )
    if (!retrievedHealthCheck) {
      GCEUtil.updateStatusAndThrowNotFoundException("Health check $healthCheckName not found for $project",
        task,
        BASE_PHASE)
    }

    // Delete all the components.
    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    listenersToDelete.each { String ruleName ->
      task.updateStatus BASE_PHASE, "Deleting listener $ruleName..."
      Operation operation = GCEUtil.deleteGlobalListener(compute, project, ruleName)
      googleOperationPoller.waitForGlobalOperation(compute, project, operation.getName(),
        timeoutSeconds, task, "listener " + ruleName, BASE_PHASE)
    }

    Operation deleteBackendServiceOp = GCEUtil.deleteIfNotInUse(
      { compute.backendServices().delete(project, backendServiceName).execute() },
      "Backend service $backendServiceName",
      project,
      task,
      BASE_PHASE
    )
    googleOperationPoller.waitForGlobalOperation(compute, project, deleteBackendServiceOp.getName(),
      timeoutSeconds, task, "backend service $backendServiceName", BASE_PHASE)

    if (description.deleteHealthChecks) {
      Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
        { compute.healthChecks().delete(project, healthCheckName).execute() },
        "Health check $healthCheckName",
        project,
        task,
        BASE_PHASE
      )
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteHealthCheckOp.getName(),
        timeoutSeconds, task, "health check $healthCheckName", BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting ssl load balancer $description.loadBalancerName."
    null
  }
}
