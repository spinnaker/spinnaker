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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class WaitForAppEngineServerGroupStartTaskSpec extends Specification {
  @Shared OortService oort
  @Shared ObjectMapper mapper = OrcaObjectMapper.newInstance()
  @Subject WaitForAppEngineServerGroupStartTask task = new WaitForAppEngineServerGroupStartTask() {
    {
      objectMapper = mapper
    }
  }

  void "should properly wait for the server group to start"() {
    setup:
      oort = Mock(OortService)
      oort.getCluster("app",
                      "my-appengine-account",
                      "app-stack-detail",
                      "appengine") >> { new Response("kato", 200, "ok", [], new TypedString(mapper.writeValueAsString(cluster))) }

      task.oortService = oort

      def context = [
        account: "my-appengine-account",
        serverGroupName: "app-stack-detail-v000",
        cloudProvider: "appengine"
      ]

      def stage = new Stage<>(new Orchestration(), "waitForServerGroupStart", context)

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
