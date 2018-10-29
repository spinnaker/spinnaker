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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DeleteGoogleHttpLoadBalancerAtomicOperation extends DeleteGoogleLoadBalancerAtomicOperation {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  @Autowired
  SafeRetry safeRetry

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

  private DeleteGoogleLoadBalancerDescription description

  DeleteGoogleHttpLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "credentials": "my-account-name", "loadBalancerName": "spin-lb", "deleteHealthChecks": false, "loadBalancerType": "HTTP"}} ]' localhost:7002/gce/ops
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
    // Target HTTP(S) proxy.
    task.updateStatus BASE_PHASE, "Retrieving target proxy $targetProxyName..."

    def retrievedTargetProxy = GCEUtil.getTargetProxyFromRule(compute, project, forwardingRule, BASE_PHASE, safeRetry, this)

    if (!retrievedTargetProxy) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target proxy $targetProxyName not found for $project", task,
          BASE_PHASE)
    }
    def urlMapName = GCEUtil.getLocalName(retrievedTargetProxy.getUrlMap())

    List<String> listenersToDelete = []
    projectForwardingRules.each { ForwardingRule rule ->
      try {
        def proxy = GCEUtil.getTargetProxyFromRule(compute, project, rule, BASE_PHASE, safeRetry, this)
        if (GCEUtil.getLocalName(proxy?.urlMap) == urlMapName) {
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

    // URL map.
    task.updateStatus BASE_PHASE, "Retrieving URL map $urlMapName..."

    // NOTE: This call is necessary because we cross-check backend services later.
    UrlMapList mapList = timeExecute(
        compute.urlMaps().list(project),
        "compute.urlMaps.list",
        TAG_SCOPE, SCOPE_GLOBAL)
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
      BackendService backendService = safeRetry.doRetry(
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
      if (backendService?.backends) {
        task.updateStatus BASE_PHASE, "Server groups still associated with Http(s) load balancer ${description.loadBalancerName}. Failing..."
        throw new IllegalStateException("Server groups still associated with Http(s) load balancer: ${description.loadBalancerName}.")
      }

      healthCheckUrls.addAll(backendService.getHealthChecks())
    }
    healthCheckUrls.unique()

    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    listenersToDelete.each { String ruleName ->
      task.updateStatus BASE_PHASE, "Deleting listener $ruleName..."
      Operation operation = GCEUtil.deleteGlobalListener(compute, project, ruleName, BASE_PHASE, safeRetry, this)
      googleOperationPoller.waitForGlobalOperation(compute, project, operation.getName(),
        timeoutSeconds, task, "listener " + ruleName, BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Deleting URL map $urlMapName..."
    Operation deleteUrlMapOperation = safeRetry.doRetry(
      { timeExecute(
            compute.urlMaps().delete(project, urlMapName),
            "compute.urlMaps.delete",
            TAG_SCOPE, SCOPE_GLOBAL) },
      "Url map $urlMapName",
      task,
      [400, 403, 412],
      [404],
      [action: "delete", phase: BASE_PHASE, operation: "compute.urlMaps.delete", (TAG_SCOPE): SCOPE_GLOBAL],
      registry
    ) as Operation

    googleOperationPoller.waitForGlobalOperation(compute, project, deleteUrlMapOperation.getName(),
        timeoutSeconds, task, "url map " + urlMapName, BASE_PHASE)

    // We make a list of the delete operations for backend services.
    List<BackendServiceAsyncDeleteOperation> deleteBackendServiceAsyncOperations =
        new ArrayList<BackendServiceAsyncDeleteOperation>()
    for (String backendServiceUrl : backendServiceUrls) {
      def backendServiceName = GCEUtil.getLocalName(backendServiceUrl)
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
      if (deleteBackendServiceOp) {
        deleteBackendServiceAsyncOperations.add(new BackendServiceAsyncDeleteOperation(
          backendServiceName: backendServiceName,
          operationName: deleteBackendServiceOp.getName()))
      }
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
        Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
          { timeExecute(
                compute.healthChecks().delete(project, healthCheckName),
                "compute.healthChecks.delete",
                TAG_SCOPE, SCOPE_GLOBAL) },
          "Http health check $healthCheckName",
          project,
          task,
          [action: 'delete', operation: 'compute.healthChecks.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_GLOBAL],
          safeRetry,
          this
        )
        if (deleteHealthCheckOp) {
          deleteHealthCheckAsyncOperations.add(new HealthCheckAsyncDeleteOperation(
            healthCheckName: healthCheckName,
            operationName: deleteHealthCheckOp.getName()))
        }
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
