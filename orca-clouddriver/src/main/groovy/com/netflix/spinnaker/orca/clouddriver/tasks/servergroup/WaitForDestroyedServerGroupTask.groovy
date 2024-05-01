/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.clouddriver.utils.ClusterDescriptor
import com.netflix.spinnaker.orca.clouddriver.utils.ServerGroupDescriptor
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class WaitForDestroyedServerGroupTask implements CloudProviderAware, RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(StageExecution stage) {
    ClusterDescriptor clusterDescriptor = getClusterDescriptor(stage)
    try {
      //TODO: figure out how to eliminate the response status code handling directly
      def response = oortService.getCluster(
          clusterDescriptor.app,
          clusterDescriptor.account,
          clusterDescriptor.name,
          clusterDescriptor.cloudProvider)

      if (response.status != 200) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      Map cluster = objectMapper.readValue(response.body.in().text, Map)
      if (!cluster || !cluster.serverGroups) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([remainingInstances: []]).build()
      }

      ServerGroupDescriptor serverGroupDescriptor = getServerGroupDescriptor(stage)
      def serverGroup = cluster.serverGroups.find {
        it.name == serverGroupDescriptor.name && it.region == serverGroupDescriptor.region
      }
      if (!serverGroup) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([remainingInstances: []]).build()
      }

      def instances = serverGroup.instances ?: []
      log.info("${serverGroupDescriptor.name}: not yet destroyed, found instances: ${instances?.join(', ') ?: 'none'}")
      return TaskResult.builder(ExecutionStatus.RUNNING).context([remainingInstances: instances.findResults { it.name }]).build()
    } catch (SpinnakerHttpException spinnakerHttpException) {
      def errorResponse = new SpinnakerServerExceptionHandler().handle(stage.name, spinnakerHttpException)
      if (spinnakerHttpException.getResponseCode() == 404) {
        return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
      } else if (spinnakerHttpException.getResponseCode() >= 500) {
        log.error("Unexpected http error (${errorResponse})")
        return TaskResult.builder(ExecutionStatus.RUNNING).context([lastSpinnakerException: errorResponse]).build()
      }

      throw spinnakerHttpException
    }
  }

}
