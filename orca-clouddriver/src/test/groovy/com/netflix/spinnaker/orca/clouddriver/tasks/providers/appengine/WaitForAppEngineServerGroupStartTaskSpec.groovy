/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class WaitForAppEngineServerGroupStartTaskSpec extends Specification {
  CloudDriverService cloudDriverService = Mock()
  @Subject WaitForAppEngineServerGroupStartTask task = new WaitForAppEngineServerGroupStartTask()

  void "should properly wait for the server group to start"() {
    setup:
      cloudDriverService.getCluster("app",
                      "my-appengine-account",
                      "app-stack-detail",
                      "appengine") >> cluster

      task.cloudDriverService = cloudDriverService

      def context = [
        account: "my-appengine-account",
        serverGroupName: "app-stack-detail-v000",
        cloudProvider: "appengine"
      ]

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newOrchestration("orca"), "waitForServerGroupStart", context)

    when:
      def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.RUNNING

    when:
      cluster.serverGroups[0].servingStatus = "SERVING"

    and:
      result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED

    where:
      cluster = [
        name: "app-stack-detail",
        account: "my-appengine-account",
        serverGroups: [
          [
            name: "app-stack-detail-v000",
            region: "us-central",
            servingStatus: "STOPPED",
          ]
        ]
      ]
  }
}
