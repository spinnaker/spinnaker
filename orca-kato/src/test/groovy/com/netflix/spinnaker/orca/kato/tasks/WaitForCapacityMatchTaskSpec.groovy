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

package com.netflix.spinnaker.orca.kato.tasks
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForCapacityMatchTaskSpec extends Specification {

  @Shared OortService oort
  @Shared ObjectMapper mapper = new OrcaObjectMapper()
  @Subject WaitForCapacityMatchTask task = new WaitForCapacityMatchTask(objectMapper: mapper)

  void "should properly wait for a scale up operation"() {
    setup:
      oort = Stub(OortService)
      oort.getCluster("kato", "test", "kato-main", "aws") >> { new Response('kato', 200, 'ok', [], new TypedString(mapper.writeValueAsString(cluster))) }
      task.oortService = oort
      def context = [account: "test", "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]]
      def stage = new OrchestrationStage(new Orchestration(), "resizeAsg", context)

    when:
      def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.RUNNING

    when:
      cluster.serverGroups[0].instances.addAll([[instanceId: "i-5678"], [instanceId: "i-0000"]])

    and:
      result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED

    where:
      cluster = [
        name: "kato-main",
        account: "test",
        serverGroups: [
          [
            name: "kato-main-v000",
            region: "us-east-1",
            instances: [
              [instanceId: "i-1234"]
            ],
            asg: [
              desiredCapacity: 3
            ]
          ]
        ]
      ]
  }

  @Unroll
  void "should return status #status for a scale up operation when server group is not disabled and instance health is #healthState"() {
    setup:
    oort = Stub(OortService)
    def cluster = [
      name: "kato-main",
      account: "test",
      serverGroups: [
        [
          name: "kato-main-v000",
          region: "us-east-1",
          instances: [
            [
              instanceId: "i-1234", health: [ [ state: 'Up' ] ]
            ]
          ],
          asg: [
            minSize: 3,
            desiredCapacity: 3
          ]
        ]
      ],
      disabled: false
    ]
    oort.getCluster("kato", "test", "kato-main", "aws") >> { new Response('kato', 200, 'ok', [], new TypedString(mapper.writeValueAsString(cluster))) }
    task.oortService = oort
    def context = [account: "test", "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]]
    def stage = new OrchestrationStage(new Orchestration(), "resizeAsg", context)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    when:
    cluster.serverGroups[0].instances.addAll([
      [instanceId: "i-5678", health: [ [ state: healthState ] ]],
      [instanceId: "i-0000", health: [ [ state: healthState ] ]]])

    and:
    result = task.execute(stage)

    then:
    result.status == status

    where:
    healthState | status
    'Down'      | ExecutionStatus.RUNNING
    'Starting'  | ExecutionStatus.RUNNING
    'Up'        | ExecutionStatus.SUCCEEDED
  }

  void "should properly wait for a scale down operation"() {
    setup:
    oort = Stub(OortService)
    oort.getCluster("kato", "test", "kato-main", "aws") >> { new Response('kato', 200, 'ok', [], new TypedString(mapper.writeValueAsString(cluster))) }
    task.oortService = oort
    def context = [account: "test", "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]]
    def stage = new OrchestrationStage(new Orchestration(), "resizeAsg", context)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    when:
    cluster.serverGroups[0].instances = [[instanceId:"i-0000"]]

    and:
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    cluster = [
      name: "kato-main",
      account: "test",
      serverGroups: [
        [
          name: "kato-main-v000",
          region: "us-east-1",
          instances: [
            [instanceId: "i-1234"],
            [instanceId: "i-5678"],
            [instanceId: "i-0000"]
          ],
          asg: [
            desiredCapacity: 1
          ]
        ]
      ]
    ]
  }
}
