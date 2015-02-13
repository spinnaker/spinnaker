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

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEOperationUtil
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class DeleteGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_NETWORK_LOAD_BALANCER"
  // The resources will probably get deleted but we should verify that the operation succeeded. 15 seconds was the
  // minimum duration it took the operation to finish while writing this code.
  private static final int DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC = 15

  static class HealthCheckAsyncDeleteOperation {
    String healthCheckName
    String operationName
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteGoogleNetworkLoadBalancerDescription description

  DeleteGoogleNetworkLoadBalancerAtomicOperation(DeleteGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleNetworkLoadBalancerDescription": { "region": "us-central1", "credentials": "my-account-name", "networkLoadBalancerName": "testlb" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of network load balancer $description.networkLoadBalancerName " +
        "in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region
    def forwardingRuleName = description.networkLoadBalancerName

    task.updateStatus BASE_PHASE, "Retrieving forwarding rule $forwardingRuleName in $region..."

    ForwardingRule forwardingRule =
        compute.forwardingRules().get(project, region, forwardingRuleName).execute()
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowException("Forwarding rule $forwardingRuleName not found in $region for $project",
          task, BASE_PHASE)
    }
    def targetPoolName = GCEUtil.getLocalName(forwardingRule.getTarget())

    task.updateStatus BASE_PHASE, "Retrieving target pool $targetPoolName in $region..."

    TargetPool targetPool = compute.targetPools().get(project, region, targetPoolName).execute()
    if (targetPool == null) {
      GCEUtil.updateStatusAndThrowException("Target pool $targetPoolName not found in $region for $project",
          task, BASE_PHASE)
    }
    def healthCheckUrls = targetPool.getHealthChecks()

    // Note that we cannot use an Elvis operator here because we might have a deleteOperationTimeoutSeconds value
    // of zero. In that case, we still want to pass that value. So we use null comparison here instead.
    def timeoutSeconds = description.deleteOperationTimeoutSeconds != null ?
        description.deleteOperationTimeoutSeconds : DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC
    def deadline = System.currentTimeMillis() + timeoutSeconds * 1000

    // Start deleting these objects. Wait between each delete operation for it to complete before continuing on to
    // delete its dependencies.
    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName in $region..."
    Operation deleteForwardingRuleOperation =
        compute.forwardingRules().delete(project, region, forwardingRuleName).execute()

    GCEOperationUtil.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOperation.getName(),
        Math.max(deadline - System.currentTimeMillis(), 0), task, "forwarding rule" + forwardingRuleName, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleting target pool $targetPoolName in $region..."
    Operation deleteTargetPoolOperation =
        compute.targetPools().delete(project, region, targetPoolName).execute()

    GCEOperationUtil.waitForRegionalOperation(compute, project, region, deleteTargetPoolOperation.getName(),
        Math.max(deadline - System.currentTimeMillis(), 0), task, "target pool" + targetPoolName, BASE_PHASE)

    // Now make a list of the delete operations for health checks.
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

    // Finally, wait on this list of these deletes to complete.
    for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
      GCEOperationUtil.waitForGlobalOperation(compute, project, asyncOperation.operationName,
          Math.max(deadline - System.currentTimeMillis(), 0), task, "health check" + asyncOperation.healthCheckName,
          BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting network load balancer $description.networkLoadBalancerName."
    null
  }


}
