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
import com.amazonaws.services.elasticloadbalancingv2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.transform.InheritConstructors
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
        DescribeLoadBalancersResult result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: [description.loadBalancerName]))
        loadBalancer = result.loadBalancers.size() > 0 ? result.loadBalancers.get(0) : null
      } catch (AmazonServiceException ignore) {
      }

      if (loadBalancer) {
        task.updateStatus BASE_PHASE, "Deleting ${description.loadBalancerName} in ${region} for ${description.credentials.name}."

        // fail if deletion protection is enabled for the load balancer
        def attributes = loadBalancing.describeLoadBalancerAttributes(new DescribeLoadBalancerAttributesRequest().withLoadBalancerArn(loadBalancer.loadBalancerArn))?.attributes ?: []
        LoadBalancerAttribute deleteProtectionAttribute = attributes.find { it.key == "deletion_protection.enabled" }
        if (deleteProtectionAttribute != null && deleteProtectionAttribute.getValue().toString().equals("true")) {
          throw new DeletionProtectionEnabledException("Load Balancer ${loadBalancer.loadBalancerName} has deletion protection enabled. Aborting delete operation.")
        }

        // Describe target groups and listeners for the load balancer.
        // We have to describe them both both first because you cant delete a target group that has a listener associated with it
        // and if you delete the listener, it loses its association with the load balancer
        DescribeTargetGroupsResult targetGroupsResult = loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancer.loadBalancerArn))
        List<TargetGroup> targetGroups = targetGroupsResult.targetGroups
        DescribeListenersResult listenersResult = loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancer.loadBalancerArn))
        List<Listener> listeners = listenersResult.listeners

        // Delete listeners
        for (Listener listener : listeners) {
          DeleteListenerRequest deleteListenerRequest = new DeleteListenerRequest(listenerArn: listener.listenerArn)
          try {
            loadBalancing.deleteListener(deleteListenerRequest)
            task.updateStatus BASE_PHASE, "Deleted listener ${listener.listenerArn} in ${region} for ${description.credentials.name}."
          } catch (AmazonServiceException ignore) {
            task.updateStatus BASE_PHASE, "Failed deleting listener ${listener.listenerArn} in ${region} for ${description.credentials.name}. Not found."
          }
        }

        // Delete target groups
        for (TargetGroup targetGroup : targetGroups) {
          DeleteTargetGroupRequest deleteTargetGroupRequest = new DeleteTargetGroupRequest(targetGroupArn: targetGroup.targetGroupArn)
          try {
            loadBalancing.deleteTargetGroup(deleteTargetGroupRequest)
            task.updateStatus BASE_PHASE, "Deleted target group ${targetGroup.targetGroupArn} in ${region} for ${description.credentials.name}."
          } catch (AmazonServiceException ignore) {
            task.updateStatus BASE_PHASE, "Failed deleting target group ${targetGroup.targetGroupArn} in ${region} for ${description.credentials.name}. Not found."
          }
        }

        // Delete load balancer
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest(loadBalancerArn: loadBalancer.loadBalancerArn)
        loadBalancing.deleteLoadBalancer(request)
      } else {
        task.updateStatus BASE_PHASE, "Failed deleting ${description.loadBalancerName} in ${region} for ${description.credentials.name}. Not found."
      }
    }
    task.updateStatus BASE_PHASE, "Done deleting ${description.loadBalancerName} in ${description.regions} for ${description.credentials.name}."
    null
  }

  @InheritConstructors
  static class DeletionProtectionEnabledException extends Exception {}

}
