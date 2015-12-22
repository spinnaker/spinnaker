/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired

class DeleteAmazonLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DeleteAmazonLoadBalancerDescription description

  DeleteAmazonLoadBalancerAtomicOperation(DeleteAmazonLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Amazon Load Balancer Operation..."
    for (region in description.regions) {
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region, true)
      DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest(loadBalancerName: description.loadBalancerName)
      task.updateStatus BASE_PHASE, "Deleting ${description.loadBalancerName} in ${region} for ${description.credentials.name}."
      loadBalancing.deleteLoadBalancer(request)
    }
    task.updateStatus BASE_PHASE, "Done deleting ${description.loadBalancerName} in ${description.regions} for ${description.credentials.name}."
    null
  }
}
