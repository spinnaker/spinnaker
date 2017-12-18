/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.OortService
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class DetermineRollbackCandidatesTaskSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)

  @Subject
  def task = new DetermineRollbackCandidatesTask(
    objectMapper,
    new RetrySupport(),
    oortService
  )

  def stage = stage {
    context = [
      credentials  : "test",
      cloudProvider: "aws",
      regions      : ["us-west-2"],
      moniker      : [
        app    : "app",
        cluster: "app-stack-details"
      ]
    ]
  }

  def "should build EXPLICIT rollback context when there are _only_ previous server groups"() {
    when: "there are previous server groups but no entity tags"
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster("app", "test", "app-stack-details", "aws") >> {
      return buildResponse([
        serverGroups: [
          buildServerGroup("servergroup-v000", "us-west-2", 50, true, [name: "my_image-0"], [:]),
          buildServerGroup("servergroup-v001", "us-west-2", 100, false, [name: "my_image-1"], [:])
        ]
      ])
    }

    result.context == [
      imagesToRestore: [
        [region: "us-west-2", image: "my_image-0", rollbackMethod: "EXPLICIT"]
      ]
    ]
    result.outputs.rollbackTypes == [
      "us-west-2": "EXPLICIT"
    ]
    result.outputs.rollbackContexts == [
      "us-west-2": [
        rollbackServerGroupName: "servergroup-v001",
        restoreServerGroupName : "servergroup-v000"
      ]
    ]
  }

  def "should build PREVIOUS_IMAGE rollback context when there are _only_ entity tags"() {
    when: "there are no previous server groups but there are entity tags"
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster("app", _, "app-stack-details", _) >> {
      return buildResponse([
        serverGroups: [
          buildServerGroup("servergroup-v001", "us-west-2", 100, false, [:], [:]),
        ]
      ])
    }
    1 * oortService.getEntityTags(_, _, _, _, _) >> {
      return [buildSpinnakerMetadata("my_image-0", "ami-xxxxx0", "5")]
    }

    result.context == [
      imagesToRestore: [
        [region: "us-west-2", image: "my_image-0", buildNumber: "5", rollbackMethod: "PREVIOUS_IMAGE"]
      ]
    ]
    result.outputs.rollbackTypes == [
      "us-west-2": "PREVIOUS_IMAGE"
    ]
    result.outputs.rollbackContexts == [
      "us-west-2": [
        rollbackServerGroupName: "servergroup-v001",
        "imageId"              : "ami-xxxxx0",
        "imageName"            : "my_image-0"
      ]
    ]
  }

  private Response buildResponse(Object value) {
    return new Response(
      "http://spinnaker",
      200,
      "OK",
      [],
      new TypedByteArray("application/json", objectMapper.writeValueAsBytes(value))
    )
  }

  private static Map buildServerGroup(String name,
                                      String region,
                                      Long createdTime,
                                      Boolean disabled,
                                      Map image,
                                      Map buildInfo) {
    return [
      name       : name,
      region     : region,
      createdTime: createdTime,
      disabled   : disabled,
      image      : image,
      buildInfo  : buildInfo
    ]
  }

  private static Map buildSpinnakerMetadata(String imageName, String imageId, String buildNumber) {
    return [
      tags: [
        [
          name : "spinnaker:metadata",
          value: [
            previousServerGroup: [
              imageName: imageName,
              imageId  : imageId,
              buildInfo: [
                jenkins: [
                  number: buildNumber
                ]
              ]
            ]
          ]
        ]
      ]
    ]
  }
}
