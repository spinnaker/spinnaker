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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.ShrinkClusterDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate

class ShrinkClusterAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "SHRINK_CLUSTER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  final ShrinkClusterDescription description
  final RestTemplate rt

  ShrinkClusterAtomicOperation(ShrinkClusterDescription description, RestTemplate rt = new RestTemplate()) {
    this.description = description
    this.rt = rt
  }

  @Override
  Void operate(List _) {
    task.updateStatus BASE_PHASE, "Initializing Cluster Shrinking Operation..."
    for (String region in description.regions) {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)

      task.updateStatus BASE_PHASE, "Looking up inactive ASGs in ${region}..."
      List<String> inactiveAsgs = getInactiveAsgs(autoScaling)
      for (String inactiveAsg : inactiveAsgs) {
        task.updateStatus BASE_PHASE, "Removing ASG -> ${inactiveAsg}"
        try {
          def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(inactiveAsg)
            .withForceDelete(description.forceDelete)
          autoScaling.deleteAutoScalingGroup(request)
          task.updateStatus BASE_PHASE, "Deleted ASG -> ${inactiveAsg}"
        } catch (IGNORE) {
        }
      }
    }
    task.updateStatus BASE_PHASE, "Finished Shrinking Cluster."
  }

  List<String> getInactiveAsgs(AmazonAutoScaling autoScaling) {
    DescribeAutoScalingGroupsResult result = autoScaling.describeAutoScalingGroups()
    List<AutoScalingGroup> asgs = []
    while (true) {
      asgs.addAll(result.autoScalingGroups)
      if (result.nextToken) {
        result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(nextToken: result.nextToken))
      } else {
        break
      }
    }
    asgs.findAll {
      def names = Names.parseName(it.autoScalingGroupName)
      description.clusterName == names.cluster && description.application == names.app
    }.findAll {
      !it.instances
    }.collect {
      it.autoScalingGroupName
    }
  }
}
