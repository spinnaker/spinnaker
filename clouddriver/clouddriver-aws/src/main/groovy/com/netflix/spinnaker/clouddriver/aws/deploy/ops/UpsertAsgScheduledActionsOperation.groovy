/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.DeleteScheduledActionRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest
import com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgScheduledActionsDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertAsgScheduledActionsOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_ASG_SCHEDULED_ACTIONS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertAsgScheduledActionsDescription description

  private IdGenerator idGenerator

  UpsertAsgScheduledActionsOperation(UpsertAsgScheduledActionsDescription description) {
    this.description = description
    this.idGenerator = new IdGenerator()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    boolean hasSucceeded = true

    String descriptor = description.asgs.findResults { "${it.serverGroupName} in ${it.region}" }.join(", ")
    task.updateStatus BASE_PHASE, "Initializing Upsert ASG Scheduled Actions operation for $descriptor..."

    for (asg in description.asgs) {
      hasSucceeded = upsertAsgScheduledActions(asg.serverGroupName, asg.region)
    }

    if (!hasSucceeded) {
      task.fail()
    } else {
      task.updateStatus BASE_PHASE, "Finished Upsert ASG Scheduled Actions operation for $descriptor."
    }
    null
  }

  private boolean upsertAsgScheduledActions(String asgName, String region) {
    try {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
      def asgResult = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
      if (!asgResult.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "No ASG named $asgName found in $region"
        return false
      }
      def existingActions = getExistingScheduledActions(asgName, region)
      def updatedActions = []

      description.scheduledActions.each { action ->
        def existingAction = existingActions.find { it.recurrence == action.recurrence }
        def actionName = asgName + '-' + idGenerator.nextId()
        def actionDescriptor = existingAction ? 'Updating' : 'Adding'
        if (existingAction) {
          actionName = existingAction.scheduledActionName
          updatedActions << existingAction
        }
        def request = new PutScheduledUpdateGroupActionRequest()
            .withAutoScalingGroupName(asgName)
            .withScheduledActionName(actionName)
            .withDesiredCapacity(action.desiredCapacity)
            .withMinSize(action.minSize)
            .withMaxSize(action.maxSize)
            .withRecurrence(action.recurrence)

        task.updateStatus BASE_PHASE, "$actionDescriptor scheduled action ${request.scheduledActionName} - recurrence: ${action.recurrence}, min: ${action.minSize}, max: ${action.maxSize}, desired: ${action.desiredCapacity}"
        autoScaling.putScheduledUpdateGroupAction(request)
      }

      existingActions.removeAll(updatedActions)

      existingActions.each {
        def request = new DeleteScheduledActionRequest()
            .withScheduledActionName(it.scheduledActionName)
            .withAutoScalingGroupName(asgName)

        task.updateStatus BASE_PHASE, "Deleting old scheduled action ${it.scheduledActionName}"
        autoScaling.deleteScheduledAction(request)
      }
      return true
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, "Could not upsert scheduled actions for ASG '$asgName' in region $region! Reason: $e.message"
      return false
    }
  }

  private List<ScheduledUpdateGroupAction> getExistingScheduledActions(String asgName, String region) {
    def actions = []
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
    def result = autoScaling.describeScheduledActions(new DescribeScheduledActionsRequest().withAutoScalingGroupName(asgName))
    while (true) {
      actions.addAll result.scheduledUpdateGroupActions
      if (result.nextToken) {
        result.withNextToken(result.nextToken)
      } else {
        break
      }
    }
    actions
  }

}
