/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class BulkDestroyServerGroupStageSpec extends Specification {

  def dynamicConfigService = Mock(DynamicConfigService)
  def bulkDestroyServerGroupStage = new BulkDestroyServerGroupStage(dynamicConfigService)

  @Unroll
  def "cache refresh for #cloudProvider with flag enabled=#enabled produces task order #expectedTaskNames"() {
    given:
    dynamicConfigService.isEnabled(
      "stages.bulk-destroy-server-group-stage.check-if-application-exists",
      true
    ) >> false
    dynamicConfigService.isEnabled(
      "stages.bulk-destroy-server-group-stage.force-cache-refresh.enabled",
      true
    ) >> enabled

    when:
    def graph = TaskNode.build(TaskNode.GraphType.FULL) {
      bulkDestroyServerGroupStage.taskGraph(stage {
        context = [cloudProvider: cloudProvider]
      }, it)
    }

    then:
    graph*.name == expectedTaskNames

    where:
    cloudProvider | enabled || expectedTaskNames
    "gce"         | true    || [
      "bulkDisableServerGroup",
      "monitorServerGroups",
      "waitForNotUpInstances",
      "forceCacheRefresh",
      "bulkDestroyServerGroup",
      "monitorServerGroups",
      "forceCacheRefresh",
      "waitForDestroyedServerGroup",
    ]
    "gce"         | false   || [
      "bulkDisableServerGroup",
      "monitorServerGroups",
      "waitForNotUpInstances",
      "bulkDestroyServerGroup",
      "monitorServerGroups",
      "waitForDestroyedServerGroup",
    ]
    "aws"         | true    || [
      "bulkDisableServerGroup",
      "monitorServerGroups",
      "waitForNotUpInstances",
      "forceCacheRefresh",
      "bulkDestroyServerGroup",
      "monitorServerGroups",
      "waitForDestroyedServerGroup",
    ]
    "aws"         | false   || [
      "bulkDisableServerGroup",
      "monitorServerGroups",
      "waitForNotUpInstances",
      "bulkDestroyServerGroup",
      "monitorServerGroups",
      "waitForDestroyedServerGroup",
    ]
  }
}
