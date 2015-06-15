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
package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeregisterInstancesFromGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation


/**
 * Remove specified instances from an existing NetworkLoadBalancer.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/targetPools/removeInstance}
 */
class DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DEREGISTER_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeregisterInstancesFromGoogleNetworkLoadBalancerDescription description

  DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperation(
    DeregisterInstancesFromGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deregisterInstancesFromGoogleNetworkLoadBalancerDescription": { "networkLoadBalancerNames": ["myapp-loadbalancer"], "instanceIds": ["myapp-dev-v000-abcd"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    def loadBalancerNames = description.networkLoadBalancerNames
    def instanceIds = description.instanceIds
    def project = description.credentials.project
    def region = description.region
    def compute = description.credentials.compute

    task.updateStatus BASE_PHASE, "Initializing deregister instances (${instanceIds.join(", ")}) from load balancers " +
      "(${loadBalancerNames.join(", ")})."

    def forwardingRules = GCEUtil.queryForwardingRules(project, region, loadBalancerNames, compute, task, BASE_PHASE)
    def instanceUrls = GCEUtil.queryInstanceUrls(project, region, instanceIds, compute, task, BASE_PHASE)

    loadBalancerNames.each { lbName ->
      def forwardingRule = forwardingRules.find { it.name == lbName }

      def targetPoolName = GCEUtil.getLocalName(forwardingRule.target)
      task.updateStatus BASE_PHASE, "Removing urls=(${instanceUrls.join(", ")}) from pool=$targetPoolName."

      def removeInstanceRequest = new TargetPoolsRemoveInstanceRequest()
      removeInstanceRequest.instances = instanceUrls.collect { url -> new InstanceReference(instance: url) }
      compute.targetPools().removeInstance(project, region, targetPoolName, removeInstanceRequest).execute()
    }

    task.updateStatus BASE_PHASE, "Done executing deregister instances (${instanceIds.join(", ")})."
    null
  }
}
