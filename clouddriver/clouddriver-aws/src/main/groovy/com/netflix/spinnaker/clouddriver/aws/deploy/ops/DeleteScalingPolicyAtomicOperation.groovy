/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.Alarm
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteScalingPolicyAtomicOperation implements AtomicOperation<Void> {

    private static final String BASE_PHASE = "DELETE_SCALING_POLICY"

    private static Task getTask() {
        TaskRepository.threadLocalTask.get()
    }

    @Autowired
    AmazonClientProvider amazonClientProvider

    private final DeleteScalingPolicyDescription description

  DeleteScalingPolicyAtomicOperation(DeleteScalingPolicyDescription description) {
        this.description = description
    }

    @Override
    Void operate(List priorOutputs) {
      task.updateStatus BASE_PHASE, "Initializing Delete Scaling Policy Operation..."
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
      String policyDescription = "${description.policyName} in ${description.region} for ${description.credentials.name}"
      task.updateStatus BASE_PHASE, "Looking up policy..."
      def policyMatches = autoScaling.describePolicies(new DescribePoliciesRequest()
          .withPolicyNames(description.policyName)
          .withAutoScalingGroupName(description.serverGroupName)
      ).scalingPolicies

      task.updateStatus BASE_PHASE, "Deleting ${policyDescription}."
      autoScaling.deletePolicy(new DeletePolicyRequest(
        autoScalingGroupName: description.serverGroupName,
        policyName: description.policyName
      ))
      task.updateStatus BASE_PHASE, "Done deleting ${policyDescription}."
      if (policyMatches.size() == 1) {
        def cloudWatch = amazonClientProvider.getCloudWatch(description.credentials, description.region, true)
        policyMatches[0].alarms.each { Alarm alarm ->
          def metricAlarms = cloudWatch.describeAlarms(new DescribeAlarmsRequest()
              .withAlarmNames(alarm.alarmName)).metricAlarms
          metricAlarms.each {
            if (it.alarmActions.isEmpty() ||
                (it.alarmActions.size() == 1 && it.alarmActions[0] == policyMatches[0].policyARN)) {
              task.updateStatus BASE_PHASE, "Deleting orphaned alarm ${alarm.alarmName}"
              cloudWatch.deleteAlarms(new DeleteAlarmsRequest()
                  .withAlarmNames(alarm.alarmName))
              task.updateStatus BASE_PHASE, "Done deleting orphaned alarm ${alarm.alarmName}"
            }
          }
        }
      }

      null
    }
}
