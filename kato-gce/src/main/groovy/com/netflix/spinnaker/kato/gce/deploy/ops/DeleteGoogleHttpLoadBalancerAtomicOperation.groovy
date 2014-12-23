/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.PathMatcher
import com.google.api.services.compute.model.PathRule
import com.google.api.services.compute.model.TargetHttpProxy
import com.google.api.services.compute.model.UrlMap
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class DeleteGoogleHttpLoadBalancerAtomicOperation  implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_HTTP_LOAD_BALANCER"
  // The resources will probably get deleted but we should verify that the operation succeeded. 15 seconds was the
  // minimum duration it took the operation to totally finish while writing this code. (Maybe add some buffer time.)
  private static final int DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC = 15

  static class HealthCheckAsyncDeleteOperation {
    String healthCheckName
    String operationName
  }

  static class BackendServiceAsyncDeleteOperation {
    String backendServiceName
    String operationName
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private static handleFinishedAsyncDeleteOperation(Operation operation, String resourceType, String resourceName) {
    if (!operation) {
      GCEUtil.updateStatusAndThrowException("Delete operation of $resourceType $resourceName timed out. The resource " +
          "may still exist.", task, BASE_PHASE)
    }
    if (operation.getError()) {
      def error = operation.getError().getErrors().get(0)
      GCEUtil.updateStatusAndThrowException("Failed to delete $resourceType $resourceName with error: $error", task,
          BASE_PHASE)
    }
    task.updateStatus BASE_PHASE, "Done deleting $resourceType $resourceName."
  }

  // Used to find all services referenced in a URL map.
  private static addServicesFromPathMatchers(List<String> backendServiceUrls, List<PathMatcher> pathMatchers) {
    for (PathMatcher pathMatcher : pathMatchers) {
      backendServiceUrls.add(pathMatcher.getDefaultService())
      for (PathRule pathRule : pathMatcher.getPathRules()) {
        backendServiceUrls.add(pathRule.getService())
      }
    }
  }

  private final DeleteGoogleHttpLoadBalancerDescription description

  DeleteGoogleHttpLoadBalancerAtomicOperation(DeleteGoogleHttpLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleHttpLoadBalancerDescription": { "credentials": "my-account-name", "loadBalancerName": "spin-lb" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of HTTP load balancer $description.loadBalancerName..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def forwardingRuleName = description.loadBalancerName

    // First we look everything up. Then, we call delete on all of it. Finally, we wait (with timeout) for all to complete.
    // Start with the forwaring rule.
    task.updateStatus BASE_PHASE, "Retrieving global forwarding rule $forwardingRuleName..."


    ForwardingRule forwardingRule = compute.globalForwardingRules().get(project, forwardingRuleName).execute()
    if (!forwardingRule) {
      GCEUtil.updateStatusAndThrowException("Global forwarding rule $forwardingRuleName not found for $project",
          task, BASE_PHASE)
    }
    def targetHttpProxyName = GCEUtil.getLocalName(forwardingRule.getTarget())

    // Target HTTP proxy.
    task.updateStatus BASE_PHASE, "Retrieving target HTTP proxy $targetHttpProxyName..."

    TargetHttpProxy targetHttpProxy = compute.targetHttpProxies().get(project, targetHttpProxyName).execute()
    if (!targetHttpProxy) {
      GCEUtil.updateStatusAndThrowException("Target http proxy $targetHttpProxyName not found for $project", task,
          BASE_PHASE)
    }
    def urlMapName = GCEUtil.getLocalName(targetHttpProxy.getUrlMap())

    // URL map.
    task.updateStatus BASE_PHASE, "Retrieving URL map $urlMapName..."

    UrlMap urlMap = compute.urlMaps().get(project, urlMapName).execute()
    List<String> backendServiceUrls = new ArrayList<String>()
    backendServiceUrls.add(urlMap.getDefaultService())
    addServicesFromPathMatchers(backendServiceUrls, urlMap.getPathMatchers())
    backendServiceUrls.unique()

    // Backend services. Also, get health check URLs.
    List<String> healthCheckUrls = new ArrayList<String>()
    for (String backendServiceUrl : backendServiceUrls) {
      def backendServiceName = GCEUtil.getLocalName(backendServiceUrl)
      task.updateStatus BASE_PHASE, "Retrieving backend service $backendServiceName..."
      BackendService backendService = compute.backendServices().get(project, backendServiceName).execute()
      healthCheckUrls.addAll(backendService.getHealthChecks())
    }
    healthCheckUrls.unique()

    // Start deleting these objects.
    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName..."
    Operation deleteForwardingRuleOperation =
        compute.globalForwardingRules().delete(project, forwardingRuleName).execute()

    task.updateStatus BASE_PHASE, "Deleting target HTTP proxy $targetHttpProxyName..."
    Operation deleteTargetHttpProxyOperation = compute.targetHttpProxies().delete(project, targetHttpProxyName).execute()

    task.updateStatus BASE_PHASE, "Deleting URL map $urlMapName..."
    Operation deleteUrlMapOperation = compute.urlMaps().delete(project, urlMapName).execute()

    // We make a list of the delete operations for backend services and health checks.
    List<BackendServiceAsyncDeleteOperation> deleteBackendServiceAsyncOperations =
        new ArrayList<BackendServiceAsyncDeleteOperation>()
    for (String backendServiceUrl : backendServiceUrls) {
      def backendServiceName = GCEUtil.getLocalName(backendServiceUrl)
      task.updateStatus BASE_PHASE, "Deleting backend service $backendServiceName for $project..."
      Operation deleteBackendServiceOp = compute.backendServices().delete(project, backendServiceName).execute()
      deleteBackendServiceAsyncOperations.add(new BackendServiceAsyncDeleteOperation(
          backendServiceName: backendServiceName,
          operationName: deleteBackendServiceOp.getName()))
    }

    List<HealthCheckAsyncDeleteOperation> deleteHealthCheckAsyncOperations =
        new ArrayList<HealthCheckAsyncDeleteOperation>()
    for (String healthCheckUrl : healthCheckUrls) {
      def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
      task.updateStatus BASE_PHASE, "Deleting health check $healthCheckName for $project..."
      Operation deleteHealthCheckOp = compute.httpHealthChecks().delete(project, healthCheckName).execute()
      deleteHealthCheckAsyncOperations.add(new HealthCheckAsyncDeleteOperation(
          healthCheckName: healthCheckName,
          operationName: deleteHealthCheckOp.getName()))
    }

    def timeoutSeconds = description.deleteOperationTimeoutSeconds != null ?
        description.deleteOperationTimeoutSeconds : DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC
    def deadline = System.currentTimeMillis() + timeoutSeconds * 1000

    // Now we wait (with timeout) for all these operations to complete.
    // TODO(bklingher): Create a wrapper for delete operations that handles polling, waiting and timeout for each step independently.
    handleFinishedAsyncDeleteOperation(
        GCEUtil.waitForGlobalOperation(compute, project, deleteForwardingRuleOperation.getName(),
            Math.max(deadline - System.currentTimeMillis(), 0)),
        "forwarding rule", forwardingRuleName)
    handleFinishedAsyncDeleteOperation(
        GCEUtil.waitForGlobalOperation(compute, project, deleteTargetHttpProxyOperation.getName(),
            Math.max(deadline - System.currentTimeMillis(), 0)),
        "target http proxy", targetHttpProxyName)
    handleFinishedAsyncDeleteOperation(
        GCEUtil.waitForGlobalOperation(compute, project, deleteUrlMapOperation.getName(),
            Math.max(deadline - System.currentTimeMillis(), 0)),
        "url map", urlMapName)

    for (BackendServiceAsyncDeleteOperation asyncOperation : deleteBackendServiceAsyncOperations) {
      handleFinishedAsyncDeleteOperation(
          GCEUtil.waitForGlobalOperation(compute, project, asyncOperation.operationName,
              Math.max(deadline - System.currentTimeMillis(), 0)),
          "backend service", asyncOperation.backendServiceName)
    }

    for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
      handleFinishedAsyncDeleteOperation(
          GCEUtil.waitForGlobalOperation(compute, project, asyncOperation.operationName,
              Math.max(deadline - System.currentTimeMillis(), 0)),
          "health check", asyncOperation.healthCheckName)
    }

    task.updateStatus BASE_PHASE, "Done deleting http load balancer $description.loadBalancerName."
    null
  }
}
