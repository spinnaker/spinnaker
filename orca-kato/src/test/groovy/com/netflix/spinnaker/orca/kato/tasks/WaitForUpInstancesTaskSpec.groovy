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
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.Status
import com.netflix.spinnaker.orca.oort.OortService
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification
import spock.lang.Subject

class WaitForUpInstancesTaskSpec extends Specification {

  @Subject task = new WaitForUpInstancesTask()

  def mapper = new ObjectMapper()

  void "should check cluster to get server groups"() {
    given:
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [
                name: "front50",
                serverGroups: [
                    [
                        region: "us-west-1",
                        name: "front50-v000",
                        asg: [
                            minSize: 1
                        ],
                        instances: [
                            [
                                isHealthy: true
                            ]
                        ]
                    ]
                ]
        ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getCluster(*_) >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."deploy.account.name" = "test"
    context."deploy.server.groups" = ["us-west-1": ["front50-v000"]]

    expect:
    task.execute(context).status == Status.SUCCEEDED

  }
}
