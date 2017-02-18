/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteAmazonLoadBalancerV2AtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_ELB_V2"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DeleteAmazonLoadBalancerDescription description

  DeleteAmazonLoadBalancerV2AtomicOperation(DeleteAmazonLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Amazon Load Balancer V2 Operation..."
    for (region in description.regions) {
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.credentials, region, true)

      // Make sure load balancer exists
      LoadBalancer loadBalancer
      try {
        DescribeLoadBalancersResult result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: [description.loadBalancerName] ))
        loadBalancer = result.loadBalancers.size() > 0 ? result.loadBalancers.get(0) : null
      } catch (AmazonServiceException ignore) {
      }

      if (loadBalancer) {
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest(loadBalancerArn: loadBalancer.loadBalancerArn)
        task.updateStatus BASE_PHASE, "Deleting ${description.loadBalancerName} in ${region} for ${description.credentials.name}."
        loadBalancing.deleteLoadBalancer(request)
      } else {
        task.updateStatus BASE_PHASE, "Failed deleting ${description.loadBalancerName} in ${region} for ${description.credentials.name}. Not found."
      }
    }
    task.updateStatus BASE_PHASE, "Done deleting ${description.loadBalancerName} in ${description.regions} for ${description.credentials.name}."
    null
  }
}
