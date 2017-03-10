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

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleAtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteGoogleLoadBalancerAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  static class HealthCheckAsyncDeleteOperation {
    String healthCheckName
    String operationName
  }

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

  DeleteGoogleLoadBalancerAtomicOperation() {}

  DeleteGoogleLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "region": "us-central1", "credentials": "my-account-name", "loadBalancerName": "testlb", "loadBalancerType": "NETWORK"}} ]' localhost:7002/gce/ops
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
      { timeExecute(
            compute.forwardingRules().get(project, region, forwardingRuleName),
            "compute.forwardingRules.get",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Regional forwarding rule $forwardingRuleName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: "compute.forwardingRules.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as ForwardingRule
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Forwarding rule $forwardingRuleName not found in $region for $project",
          task, BASE_PHASE)
    }
    def targetPoolName = GCEUtil.getLocalName(forwardingRule.getTarget())

    task.updateStatus BASE_PHASE, "Retrieving target pool $targetPoolName in $region..."

    TargetPool targetPool = safeRetry.doRetry(
      { timeExecute(
            compute.targetPools().get(project, region, targetPoolName),
            "compute.targetPools.get",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Target pool $targetPoolName",
      task,
      [400, 403, 412],
      [],
      [action: "get", phase: BASE_PHASE, operation: "compute.targetPools.get", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as TargetPool
    if (targetPool == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target pool $targetPoolName not found in $region for $project",
          task, BASE_PHASE)
    }
    if (targetPool?.instances?.size > 0) {
      task.updateStatus BASE_PHASE, "Server groups still associated with network load balancer ${description.loadBalancerName}. Failing..."
      throw new IllegalStateException("Server groups still associated with network load balancer: ${description.loadBalancerName}.")
    }

    def healthCheckUrls = targetPool.getHealthChecks()

    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    // Start deleting these objects. Wait between each delete operation for it to complete before continuing on to
    // delete its dependencies.
    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName in $region..."
    Operation deleteForwardingRuleOperation = safeRetry.doRetry(
      { timeExecute(
            compute.forwardingRules().delete(project, region, forwardingRuleName),
            "compute.forwardingRules.delete",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Regional forwarding rule $forwardingRuleName",
      task,
      [400, 403, 412],
      [404],
      [action: "delete", phase: BASE_PHASE, operation: "compute.forwardingRules.delete", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as Operation

    googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOperation.getName(),
        timeoutSeconds, task, "forwarding rule " + forwardingRuleName, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleting target pool $targetPoolName in $region..."
    Operation deleteTargetPoolOperation = safeRetry.doRetry(
      { timeExecute(
            compute.targetPools().delete(project, region, targetPoolName),
            "compute.targetPools.delete",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
      "Target pool $targetPoolName",
      task,
      [400, 403, 412],
      [404],
      [action: "delete", phase: BASE_PHASE, operation: "compute.targetPools.delete", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
      registry
    ) as Operation

    googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteTargetPoolOperation.getName(),
      timeoutSeconds, task, "target pool " + targetPoolName, BASE_PHASE)

    // Now make a list of the delete operations for health checks if the description says to do so.
    if (description.deleteHealthChecks) {
      List<HealthCheckAsyncDeleteOperation> deleteHealthCheckAsyncOperations =
          new ArrayList<HealthCheckAsyncDeleteOperation>()
      for (String healthCheckUrl : healthCheckUrls) {
        def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
        Operation deleteHealthCheckOp = GCEUtil.deleteIfNotInUse(
          { timeExecute(
                compute.httpHealthChecks().delete(project, healthCheckName),
                "compute.httpHealthChecks.delete",
                TAG_SCOPE, SCOPE_GLOBAL) },
          "Http health check $healthCheckName",
          project,
          task,
          [action: 'delete', operation: 'compute.httpsHealthChecks.delete', phase: BASE_PHASE, (TAG_SCOPE): SCOPE_GLOBAL],
          safeRetry,
          this
        )
        if (deleteHealthCheckOp) {
          deleteHealthCheckAsyncOperations.add(new HealthCheckAsyncDeleteOperation(
            healthCheckName: healthCheckName,
            operationName: deleteHealthCheckOp.getName()))
        }
      }

      // Finally, wait on this list of these deletes to complete.
      for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
        googleOperationPoller.waitForGlobalOperation(compute, project, asyncOperation.operationName,
            timeoutSeconds, task, "health check " + asyncOperation.healthCheckName,
            BASE_PHASE)
      }
    }

    task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName in $region."
    null
  }
}
