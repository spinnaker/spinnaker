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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertLoadBalancerTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  static final String CLOUD_OPERATION_TYPE = "upsertLoadBalancer"

  @Override
  long getBackoffPeriod() {
    return 2000
  }

  @Override
  long getTimeout() {
    return 60000
  }

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def context = new HashMap(stage.context)
    if (!context.name) {
      if (context.clusterName) {
        context.name = "${stage.context.clusterName}-frontend"
      } else if (context.loadBalancerName) {
        context.name = context.loadBalancerName
      } else {
        throw new IllegalArgumentException("Context for ${CLOUD_OPERATION_TYPE} is missing a name and has no default fallback options.")
      }
    }

    context.availabilityZones = context.availabilityZones ?: [(context.region): context.regionZones]

    def operations = [
      [(CLOUD_OPERATION_TYPE): context]
    ]

    def taskId = kato.requestOperations(cloudProvider, operations)
      .toBlocking()
      .first()

    Map outputs = [
      "notification.type"   : CLOUD_OPERATION_TYPE.toLowerCase(),
      "kato.result.expected": true,
      "kato.last.task.id"   : taskId,
      "targets"             : operations.collect {
        [
          credentials      : account,
          availabilityZones: it[CLOUD_OPERATION_TYPE].availabilityZones,
          vpcId            : it[CLOUD_OPERATION_TYPE].vpcId,
          name             : it[CLOUD_OPERATION_TYPE].name,
        ]
      }
    ]

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }
}
