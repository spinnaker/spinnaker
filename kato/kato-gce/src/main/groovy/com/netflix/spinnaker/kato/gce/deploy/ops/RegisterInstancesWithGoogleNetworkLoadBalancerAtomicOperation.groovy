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
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.gce.deploy.description.RegisterInstancesWithGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation


/**
 * Add additional instances to an existing NetworkLoadBalancer.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/targetPools/addInstance}
 */
class RegisterInstancesWithGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "REGISTER_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RegisterInstancesWithGoogleNetworkLoadBalancerDescription description

  RegisterInstancesWithGoogleNetworkLoadBalancerAtomicOperation(
    RegisterInstancesWithGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "registerInstancesWithGoogleNetworkLoadBalancerDescription": { "networkLoadBalancerNames": ["myapp-loadbalancer"], "instanceIds": ["myapp-dev-v000-abcd"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    def loadBalancerNames = description.networkLoadBalancerNames
    def instanceIds = description.instanceIds
    def project = description.credentials.project
    def region = description.region
    def compute = description.credentials.compute

    task.updateStatus BASE_PHASE, "Initializing register instances (${instanceIds.join(", ")}) with load balancers " +
      "(${loadBalancerNames.join(", ")})."

    def forwardingRules = GCEUtil.queryForwardingRules(project, region, loadBalancerNames, compute, task, BASE_PHASE)
    def instanceUrls = GCEUtil.queryInstanceUrls(project, region, instanceIds, compute, task, BASE_PHASE)

    loadBalancerNames.each { lbName ->
      def forwardingRule = forwardingRules.find { it.name == lbName }

      def targetPoolName = GCEUtil.getLocalName(forwardingRule.target)
      task.updateStatus BASE_PHASE, "Adding urls=(${instanceUrls.join(", ")}) to pool=$targetPoolName."

      def addInstanceRequest = new TargetPoolsAddInstanceRequest()
      addInstanceRequest.instances = instanceUrls.collect{ url -> new InstanceReference(instance: url) }
      compute.targetPools().addInstance(project, region, targetPoolName, addInstanceRequest).execute()
    }

    task.updateStatus BASE_PHASE, "Done executing register instances (${instanceIds.join(", ")})."
    null
  }
}
