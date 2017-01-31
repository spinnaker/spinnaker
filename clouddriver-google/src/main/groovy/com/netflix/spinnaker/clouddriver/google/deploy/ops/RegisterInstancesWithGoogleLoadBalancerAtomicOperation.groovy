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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.RegisterInstancesWithGoogleLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired

/**
 * Add additional instances to an existing NetworkLoadBalancer.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/targetPools/addInstance}
 */
class RegisterInstancesWithGoogleLoadBalancerAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "REGISTER_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RegisterInstancesWithGoogleLoadBalancerDescription description

  @Autowired
  SafeRetry safeRetry

  RegisterInstancesWithGoogleLoadBalancerAtomicOperation(
    RegisterInstancesWithGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "registerInstancesWithLoadBalancer": { "loadBalancerNames": ["myapp-loadbalancer"], "instanceIds": ["myapp-dev-v000-abcd"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing registration of instances (${description.instanceIds.join(", ")}) " +
      "with load balancers (${description.loadBalancerNames.join(", ")}) in $description.region..."

    def loadBalancerNames = description.loadBalancerNames
    def instanceIds = description.instanceIds
    def project = description.credentials.project
    def region = description.region
    def compute = description.credentials.compute

    def forwardingRules = GCEUtil.queryRegionalForwardingRules(project, region, loadBalancerNames, compute, task, BASE_PHASE, safeRetry, this)
    def instanceUrls = GCEUtil.queryInstanceUrls(project, region, instanceIds, compute, task, BASE_PHASE, this)

    loadBalancerNames.each { lbName ->
      def forwardingRule = forwardingRules.find { it.name == lbName }

      def targetPoolName = GCEUtil.getLocalName(forwardingRule.target)
      task.updateStatus BASE_PHASE, "Adding urls (${instanceUrls.join(", ")}) to target pool $targetPoolName."

      def addInstanceRequest = new TargetPoolsAddInstanceRequest()
      addInstanceRequest.instances = instanceUrls.collect{ url -> new InstanceReference(instance: url) }
      timeExecute(
          compute.targetPools().addInstance(project, region, targetPoolName, addInstanceRequest),
          "compute.targetPools.addInstance",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }

    task.updateStatus BASE_PHASE, "Done registering instances (${instanceIds.join(", ")}) with load balancers " +
      "(${loadBalancerNames.join(", ")}) in $region."
    null
  }
}
