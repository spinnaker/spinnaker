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
package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy.ScalingPolicyCopier
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import groovy.transform.Canonical

@Canonical
class AsgReferenceCopier {

  static final DIMENSION_NAME_FOR_ASG = 'AutoScalingGroupName'

  final AmazonClientProvider amazonClientProvider

  final NetflixAmazonCredentials sourceCredentials
  final String sourceRegion

  final NetflixAmazonCredentials targetCredentials
  final String targetRegion

  IdGenerator idGenerator

  void copyScheduledActionsForAsg(Task task, String sourceAsgName, String targetAsgName) {
    AmazonAutoScaling sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceCredentials, sourceRegion, true)
    AmazonAutoScaling targetAutoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion, true)
    def sourceScheduledActions = new ScheduledActionsRetriever(sourceAutoScaling).retrieve(new DescribeScheduledActionsRequest(autoScalingGroupName: sourceAsgName))
    sourceScheduledActions.each { sourceScheduledAction ->
      String newScheduledActionName = [targetAsgName, 'schedule', idGenerator.nextId()].join('-')
      def request = new PutScheduledUpdateGroupActionRequest(
        autoScalingGroupName: targetAsgName,
        scheduledActionName: newScheduledActionName,
        endTime: sourceScheduledAction.endTime,
        recurrence: sourceScheduledAction.recurrence,
        minSize: sourceScheduledAction.minSize,
        maxSize: sourceScheduledAction.maxSize,
        desiredCapacity: sourceScheduledAction.desiredCapacity
      )
      Date startTime = sourceScheduledAction.startTime ?: sourceScheduledAction.time
      if (startTime?.time > System.currentTimeMillis()) {
        request.withStartTime(startTime)
      }
      targetAutoScaling.putScheduledUpdateGroupAction(request)

      task.updateStatus "AWS_DEPLOY", "Creating scheduled action (${request}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
  }

  @Canonical
  static class ScheduledActionsRetriever extends AwsResultsRetriever<ScheduledUpdateGroupAction, DescribeScheduledActionsRequest, DescribeScheduledActionsResult> {
    final AmazonAutoScaling autoScaling

    @Override
    protected DescribeScheduledActionsResult makeRequest(DescribeScheduledActionsRequest request) {
      autoScaling.describeScheduledActions(request)
    }

    @Override
    protected List<ScheduledUpdateGroupAction> accessResult(DescribeScheduledActionsResult result) {
      result.scheduledUpdateGroupActions
    }
  }

}
