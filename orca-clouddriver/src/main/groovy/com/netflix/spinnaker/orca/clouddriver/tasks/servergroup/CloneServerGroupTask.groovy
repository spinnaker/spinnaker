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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.clone.CloneDescriptionDecorator
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CloneServerGroupTask extends AbstractCloudProviderAwareTask implements Task, DeploymentDetailsAware {
  @Autowired
  Collection<CloneDescriptionDecorator> cloneDescriptionDecorators = []

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def operation = [:]
    operation.putAll(stage.context)
    String targetRegion = operation.region ?: operation.availabilityZones?.keySet()?.getAt(0) ?: operation.source?.region
    String cloudProvider = getCloudProvider(stage)
    withImageFromPrecedingStage(stage, targetRegion, cloudProvider) {
      operation.amiName = operation.amiName ?: it.amiName
      operation.imageId = operation.imageId ?: it.imageId
      operation.image = operation.image ?: it.imageId
    }

    withImageFromDeploymentDetails(stage, targetRegion, cloudProvider) {
      operation.amiName = operation.amiName ?: it.amiName
      operation.imageId = operation.imageId ?: it.imageId
      operation.image = operation.image ?: it.imageId
    }

    String credentials = getCredentials(stage)
    def taskId = kato.requestOperations(cloudProvider, getDescriptions(stage, operation)).toBlocking().first()

    def outputs = [
      "notification.type"   : "createcopylastasg",
      "kato.result.expected": true,
      "kato.last.task.id"   : taskId,
      "deploy.account.name" : credentials,
    ]

    if (stage.context.suspendedProcesses) {
      def suspendedProcesses = stage.context.suspendedProcesses as Set<String>
      if (suspendedProcesses?.contains("AddToLoadBalancer")) {
        outputs.interestingHealthProviderNames = HealthHelper.getInterestingHealthProviderNames(stage, ["Amazon"])
      }
    }

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  private List<Map<String, Object>> getDescriptions(Stage stage, Map operation) {
    log.info("Generating descriptions (cloudProvider: ${operation.cloudProvider}, getCloudProvider: ${getCloudProvider(operation)}, credentials: ${operation.credentials}, availabilityZones: ${operation.availabilityZones})")

    List<Map<String, Object>> descriptions = [[cloneServerGroup: operation]]
    cloneDescriptionDecorators.each { decorator ->
      if (decorator.shouldDecorate(operation)) {
        decorator.decorate(operation, descriptions, stage)
      }
    }
    descriptions
  }
}
