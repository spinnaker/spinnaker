/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DeleteGoogleHttpLoadBalancerAtomicOperation  implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_HTTP_LOAD_BALANCER"

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

  // Used to find all services referenced in a URL map.
  private static void addServicesFromPathMatchers(List<String> backendServiceUrls, List<PathMatcher> pathMatchers) {
    for (PathMatcher pathMatcher : pathMatchers) {
      backendServiceUrls.add(pathMatcher.getDefaultService())
      for (PathRule pathRule : pathMatcher.getPathRules()) {
        backendServiceUrls.add(pathRule.getService())
      }
    }
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private final DeleteGoogleHttpLoadBalancerDescription description

  DeleteGoogleHttpLoadBalancerAtomicOperation(DeleteGoogleHttpLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleHttpLoadBalancerDescription": { "credentials": "my-account-name", "loadBalancerName": "spin-lb", "deleteHealthChecks": false }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of HTTP load balancer $description.loadBalancerName..."

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
      GCEUtil.updateStatusAndThrowNotFoundException("Global forwarding rule $forwardingRuleName not found for $project",
          task, BASE_PHASE)
    }
    String targetProxyType = Utils.getTargetProxyType(forwardingRule.target)
    String targetProxyName = GCEUtil.getLocalName(forwardingRule.target)

    // Target HTTP(S) proxy.
    task.updateStatus BASE_PHASE, "Retrieving target proxy $targetProxyName..."

    def retrievedTargetProxy = null

    switch (targetProxyType) {
      case "targetHttpProxies":
        retrievedTargetProxy = compute.targetHttpProxies().get(project, targetProxyName).execute()
        break
      case "targetHttpsProxies":
        retrievedTargetProxy = compute.targetHttpsProxies().get(project, targetProxyName).execute()
        break
      default:
        log.warn("Unexpected target proxy type for $targetProxyName.")
        retrievedTargetProxy = null
        break
    }

    if (!retrievedTargetProxy) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target proxy $targetProxyName not found for $project", task,
          BASE_PHASE)
    }
    def urlMapName = GCEUtil.getLocalName(retrievedTargetProxy.getUrlMap())

    // URL map.
    task.updateStatus BASE_PHASE, "Retrieving URL map $urlMapName..."

    // NOTE: This call is necessary because we cross-check backend services later.
    UrlMapList mapList = compute.urlMaps().list(project).execute()
    List<UrlMap> projectUrlMaps = mapList.getItems()

    UrlMap urlMap = projectUrlMaps.find { it.name == urlMapName }
    projectUrlMaps.removeAll { it.name == urlMapName }

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

      if (backendService?.backends) {
        task.updateStatus BASE_PHASE, "Server groups still associated with Http(s) load balancer ${description.loadBalancerName}. Failing..."
        throw new IllegalStateException("Server groups still associated with Http(s) load balancer: ${description.loadBalancerName}.")
      }
      if (GCEUtil.isBackendServiceInUse(projectUrlMaps, backendServiceName)) {
        task.updateStatus BASE_PHASE, "Backend service in use by another Http(s) load balancer. Failing..."
        throw new IllegalStateException("Backend service in use by another Http(s) load balancer. Cannot destroy.")
      }

      healthCheckUrls.addAll(backendService.getHealthChecks())
    }
    healthCheckUrls.unique()

    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    // Start deleting these objects. Wait between each delete operation for it to complete before continuing on to
    // delete its dependencies.
    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName..."
    Operation deleteForwardingRuleOperation =
        compute.globalForwardingRules().delete(project, forwardingRuleName).execute()

    googleOperationPoller.waitForGlobalOperation(compute, project, deleteForwardingRuleOperation.getName(),
        timeoutSeconds, task, "forwarding rule " + forwardingRuleName, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleting target proxy $targetProxyName..."

    Operation deleteTargetProxyOperation  = null
    switch (targetProxyType) {
      case "targetHttpProxies":
        deleteTargetProxyOperation = compute.targetHttpProxies().delete(project, targetProxyName).execute()
        break
      case "targetHttpsProxies":
        deleteTargetProxyOperation = compute.targetHttpsProxies().delete(project, targetProxyName).execute()
        break
      default:
        log.warn("Unexpected target proxy type for $targetProxyName. Cannot delete target proxy.")
        break
    }

    googleOperationPoller.waitForGlobalOperation(compute, project, deleteTargetProxyOperation.getName(),
        timeoutSeconds, task, "target proxy" + targetProxyName, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleting URL map $urlMapName..."
    Operation deleteUrlMapOperation = compute.urlMaps().delete(project, urlMapName).execute()

    googleOperationPoller.waitForGlobalOperation(compute, project, deleteUrlMapOperation.getName(),
        timeoutSeconds, task, "url map " + urlMapName, BASE_PHASE)

    // We make a list of the delete operations for backend services.
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

    // Wait on all of these deletes to complete.
    for (BackendServiceAsyncDeleteOperation asyncOperation : deleteBackendServiceAsyncOperations) {
      googleOperationPoller.waitForGlobalOperation(compute, project, asyncOperation.operationName,
          timeoutSeconds, task, "backend service " + asyncOperation.backendServiceName, BASE_PHASE)
    }

    // Now make a list of the delete operations for health checks if description says to do so.
    if (description.deleteHealthChecks) {
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

      // Finally, wait on all of these deletes to complete.
      for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
        googleOperationPoller.waitForGlobalOperation(compute, project, asyncOperation.operationName,
            timeoutSeconds, task, "health check " + asyncOperation.healthCheckName,
            BASE_PHASE)
      }
    }

    task.updateStatus BASE_PHASE, "Done deleting http load balancer $description.loadBalancerName."
    null
  }
}
