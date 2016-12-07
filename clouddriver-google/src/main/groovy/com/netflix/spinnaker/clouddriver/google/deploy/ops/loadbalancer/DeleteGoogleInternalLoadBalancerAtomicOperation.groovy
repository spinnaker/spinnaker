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

import com.google.api.client.json.GenericJson
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
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DeleteGoogleInternalLoadBalancerAtomicOperation implements AtomicOperation<Void> {
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

    ForwardingRule forwardingRule = safeRetry.doRetry(
      { compute.forwardingRules().get(project, region, forwardingRuleName).execute() },
      'Get',
      "Regional forwarding rule $forwardingRuleName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      []
    ) as ForwardingRule
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Forwarding rule $forwardingRuleName not found in $region for $project",
        task, BASE_PHASE)
    }

    def backendServiceName = GCEUtil.getLocalName(forwardingRule.backendService)

    task.updateStatus BASE_PHASE, "Retrieving backend service $backendServiceName in $region..."

    BackendService backendService = safeRetry.doRetry(
      { compute.regionBackendServices().get(project, region, backendServiceName).execute() },
      'Get',
      "Region backend service $backendServiceName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      []
    ) as BackendService
    if (backendService == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Backend service $backendServiceName not found in $region for $project",
        task, BASE_PHASE)
    }

    def healthCheckUrl = backendService.healthChecks[0]
    def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
    def healthCheckType = Utils.getHealthCheckType(healthCheckUrl)
    def healthCheckGet = null
    switch (healthCheckType) {
      case "httpHealthChecks":
        healthCheckGet = { compute.httpHealthChecks().get(project, healthCheckName).execute() }
        break
      case "httpsHealthChecks":
        healthCheckGet = { compute.httpsHealthChecks().get(project, healthCheckName).execute() }
        break
      case "healthChecks":
        healthCheckGet = { compute.healthChecks().get(project, healthCheckName).execute() }
        break
      default:
        throw new IllegalStateException("Unknown health check type for health check named: ${healthCheckName}.")
        break
    }

    // GenericJson is the only base class the health checks share...
    def healthCheck = safeRetry.doRetry(
      healthCheckGet,
      'Get',
      "Health check $healthCheckName",
      task,
      BASE_PHASE,
      [400, 403, 412],
      []
    )
    if (healthCheck == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Health check $healthCheckName not found for $project",
        task, BASE_PHASE)
    }

    // Now delete all the components, waiting for each delete operation to finish.
    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    Operation deleteForwardingRuleOp = safeRetry.doRetry(
      { compute.forwardingRules().delete(project, region, forwardingRuleName).execute() },
      'Delete',
      "Regional forwarding rule $forwardingRuleName",
      task,
      BASE_PHASE,
      [400, 412],
      [404]
    ) as Operation

    if (deleteForwardingRuleOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOp.getName(),
        timeoutSeconds, task, "Regional forwarding rule $forwardingRuleName", BASE_PHASE)
    }

    Operation deleteBackendServiceOp = GCEUtil.deleteIfNotInUse(
      { compute.regionBackendServices().delete(project, region, backendServiceName).execute() },
      "Region backend service $backendServiceName",
      project,
      task,
      BASE_PHASE,
      safeRetry
    )
    if (deleteBackendServiceOp) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteBackendServiceOp.getName(),
        timeoutSeconds, task, "Regional backend service $backendServiceName", BASE_PHASE)
    }

    Closure<Operation> deleteHealthCheckClosure = null
    switch (healthCheckType) {
      case "httpHealthChecks":
        deleteHealthCheckClosure = { compute.httpHealthChecks().delete(project, healthCheckName).execute() }
        break
      case "httpsHealthChecks":
        deleteHealthCheckClosure = { compute.httpsHealthChecks().delete(project, healthCheckName).execute() }
        break
      case "healthChecks":
        deleteHealthCheckClosure = { compute.healthChecks().delete(project, healthCheckName).execute() }
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
      BASE_PHASE,
      safeRetry
    )
    if (deleteHealthCheckOp) {
      googleOperationPoller.waitForGlobalOperation(compute, project, deleteHealthCheckOp.getName(),
        timeoutSeconds, task, "Health check $healthCheckName", BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting internal load balancer $description.loadBalancerName in $region."
    null
  }
}
