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

package com.netflix.spinnaker.kato.gce.deploy.ops.loadbalancer

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteGoogleLoadBalancerAtomicOperation implements AtomicOperation<Void> {
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

  private final DeleteGoogleLoadBalancerDescription description

  DeleteGoogleLoadBalancerAtomicOperation(DeleteGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "region": "us-central1", "credentials": "my-account-name", "loadBalancerName": "testlb" }} ]' localhost:7002/gce/ops
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

    ForwardingRule forwardingRule =
        compute.forwardingRules().get(project, region, forwardingRuleName).execute()
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Forwarding rule $forwardingRuleName not found in $region for $project",
          task, BASE_PHASE)
    }
    def targetPoolName = GCEUtil.getLocalName(forwardingRule.getTarget())

    task.updateStatus BASE_PHASE, "Retrieving target pool $targetPoolName in $region..."

    TargetPool targetPool = compute.targetPools().get(project, region, targetPoolName).execute()
    if (targetPool == null) {
      GCEUtil.updateStatusAndThrowNotFoundException("Target pool $targetPoolName not found in $region for $project",
          task, BASE_PHASE)
    }
    def healthCheckUrls = targetPool.getHealthChecks()

    def timeoutSeconds = description.deleteOperationTimeoutSeconds

    // Start deleting these objects. Wait between each delete operation for it to complete before continuing on to
    // delete its dependencies.
    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName in $region..."
    Operation deleteForwardingRuleOperation =
        compute.forwardingRules().delete(project, region, forwardingRuleName).execute()

    googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOperation.getName(),
        timeoutSeconds, task, "forwarding rule " + forwardingRuleName, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleting target pool $targetPoolName in $region..."
    Operation deleteTargetPoolOperation =
        compute.targetPools().delete(project, region, targetPoolName).execute()

    googleOperationPoller.waitForRegionalOperation(compute, project, region, deleteTargetPoolOperation.getName(),
        timeoutSeconds, task, "target pool " + targetPoolName, BASE_PHASE)

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
      googleOperationPoller.waitForGlobalOperation(compute, project, asyncOperation.operationName,
          timeoutSeconds, task, "health check " + asyncOperation.healthCheckName,
          BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName in $region."
    null
  }

}
