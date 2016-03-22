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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertAlarmAtomicOperation implements AtomicOperation<Map<String, String>> {

  UpsertAlarmDescription description

  UpsertAlarmAtomicOperation(UpsertAlarmDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  IdGenerator IdGenerator = new IdGenerator()

  @Override
  Map<String, String> operate(List priorOutputs) {

    description.name = description.name ?: "${description.asgName ?: ""}${description.asgName ? "-" : ""}alarm-${idGenerator.nextId()}"

    if (description.asgName) {
      description.dimensions = description.dimensions ?: []
      description.dimensions.add(new Dimension(name: "AutoScalingGroupName", value: description.asgName))
    }

    final previousUpsertScalingPolicyResult = (UpsertScalingPolicyResult) priorOutputs.reverse().find {
      it instanceof UpsertScalingPolicyResult
    }
    if (previousUpsertScalingPolicyResult) {
      description.alarmActionArns = description.alarmActionArns ?: []
      description.alarmActionArns.add(previousUpsertScalingPolicyResult.policyArn)
    }

    def request = description.buildRequest()
    def cloudWatch = amazonClientProvider.getCloudWatch(description.credentials, description.region, true)
    cloudWatch.putMetricAlarm(request)

    [alarmName: description.name.toString()]
  }

}
