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

import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import retrofit.client.Response
import retrofit.mime.TypedInput

/**
 * Created by aglover on 7/10/14.
 */
class AsgActionWaitForDownInstancesTaskSpec extends Specification {
  @Subject task = new AsgActionWaitForDownInstancesTask()

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
            [
                name        : "front50",
                serverGroups: [
                    [
                        region   : "us-west-1",
                        name     : "front50-v000",
                        asg      : [
                            minSize: 1
                        ],
                        instances: [
                            [
                                isHealthy: false
                            ]
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
    context."targetop.asg.enableAsg.name" = "front50"
    context."targetop.asg.enableAsg.regions" = ['us-west-1']
    context."deploy.account.name" = "test"

    expect:
    task.execute(context).status == TaskResult.Status.SUCCEEDED

  }
}
