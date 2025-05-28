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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.clouddriver.OortService
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification

import static java.net.HttpURLConnection.HTTP_NOT_FOUND

/**
 * TODO(rz): Fix this. I'm not sure what's going on here.
 *
 * {@code
 * startup failed:
 * .../orca/.../SpinnakerMetadataServerGroupTagGeneratorSpec.groovy: 61: unable to resolve class PipelineExecutionImpl.AuthenticationDetails
 *  @ line 61, column 44.
 *    ication = authenticatedUser ? new Pipeli
 *                                  ^
 * }
 */
class SpinnakerMetadataServerGroupTagGeneratorSpec extends Specification {
  def oortService = Mock(OortService)
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  @Shared
  def notFoundError = makeSpinnakerHttpException(HTTP_NOT_FOUND)

//  @Unroll
//  def "should build spinnaker:metadata tag for pipeline"() {
//    given:
//    def tagGenerator = Spy(SpinnakerMetadataServerGroupTagGenerator, constructorArgs: [oortService, retrySupport]) {
//      1 * getPreviousServerGroupFromClusterByTarget(_, _, _, _, _, "NEWEST") >> { return newestServerGroup }
//
//      if (newestServerGroup != null && newestServerGroup != previousServerGroup) {
//        1 * getPreviousServerGroupFromClusterByTarget(_, _, _, _, _, "ANCESTOR") >> { return previousServerGroup }
//      }
//    }
//
//    def pipeline = pipeline {
//      name = "my pipeline"
//      application = "application"
//      pipelineConfigId = "configId"
//      authentication = authenticatedUser ? new PipelineExecutionImpl.AuthenticationDetails(authenticatedUser) : null
//
//      stage {
//        type = "wait"
//        context = [comments: "this is a wait stage"]
//      }
//    }
//
//    when:
//    def tags = tagGenerator.generateTags(pipeline.stages[0], "application-v002", "account", "us-west-2", "aws")
//
//    then:
//    tags == [[
//               name     : "spinnaker:metadata",
//               namespace: "spinnaker",
//               value    : [
//                 executionId     : pipeline.id,
//                 pipelineConfigId: "configId",
//                 application     : "application",
//                 executionType   : "pipeline",
//                 description     : "my pipeline",
//                 stageId         : pipeline.stages[0].id,
//                 comments        : "this is a wait stage",
//               ] + (previousServerGroup ? [previousServerGroup: previousServerGroup] : [:])
//                 + (authenticatedUser ? [user: authenticatedUser] : [:])
//             ]]
//
//    where:
//    newestServerGroup          | previousServerGroup        | authenticatedUser || _
//    null                       | null                       | null              || _    // metadata tag should NOT include `previousServerGroup`
//    null                       | null                       | "username"        || _    // include user if non-null
//    [name: "application-v002"] | [name: "application-v001"] | null              || _    // NEWEST is checked first, falling back to ANCESTOR
//    [name: "application-v001"] | [name: "application-v001"] | null              || _    // NEWEST is still cached as the ANCESTOR, no fallback
//  }

//  @Unroll
//  def "should build spinnaker:metadata tag for orchestration"() {
//    given:
//    def tagGenerator = Spy(SpinnakerMetadataServerGroupTagGenerator, constructorArgs: [oortService, retrySupport]) {
//      1 * getPreviousServerGroupFromClusterByTarget(_, _, _, _, _, "NEWEST") >> { return newestServerGroup }
//      if (newestServerGroup != null && newestServerGroup != previousServerGroup) {
//        1 * getPreviousServerGroupFromClusterByTarget(_, _, _, _, _, "ANCESTOR") >> { return previousServerGroup }
//      }
//    }
//
//    def orchestration = orchestration {
//      name = "my orchestration"
//      application = "application"
//      authentication = authenticatedUser ? new PipelineExecution.AuthenticationDetails(authenticatedUser) : null
//      description = "this is my orchestration"
//
//      stages << new StageExecutionImpl(delegate, "wait")
//    }
//
//    when:
//    def tags = tagGenerator.generateTags(orchestration.stages[0], "application-v002", "account", "us-west-2", "aws")
//
//    then:
//    tags == [[
//               name     : "spinnaker:metadata",
//               namespace: "spinnaker",
//               value    : [
//                 executionId  : orchestration.id,
//                 application  : "application",
//                 executionType: "orchestration",
//                 description  : "this is my orchestration",
//                 stageId      : orchestration.stages[0].id,
//               ] + (previousServerGroup ? [previousServerGroup: previousServerGroup] : [:])
//                 + (authenticatedUser ? [user: authenticatedUser] : [:])
//             ]]
//
//    where:
//    newestServerGroup          | previousServerGroup        | authenticatedUser || _
//    null                       | null                       | null              || _    // metadata tag should NOT include `previousServerGroup`
//    null                       | null                       | "username"        || _    // include user if non-null
//    [name: "application-v002"] | [name: "application-v001"] | null              || _    // NEWEST is queried, then falls back to ANCESTOR
//    [name: "application-v001"] | [name: "application-v001"] | null              || _    // NEWEST is still cached as the ANCESTOR, no fallback
//  }

  def "should construct previous server group metadata when present"() {
    given:
    def tagGenerator = new SpinnakerMetadataServerGroupTagGenerator(oortService, retrySupport)

    when: "previous server does exist"
    def previousServerGroupMetadata = tagGenerator.getPreviousServerGroupFromCluster(
      "application", "account", "cluster", "aws", "us-west-2", "application-v002"
    )

    then: "metadata should be returned"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "aws", "us-west-2", "NEWEST", "image", "true") >> {
      Calls.response( [
        serverGroupName: "application-v002",
        imageId        : "ami-f234567",
        imageName      : "my_image"
      ])
    }
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "aws", "us-west-2", "ANCESTOR", "image", "true") >> {
      Calls.response( [
        serverGroupName: "application-v001",
        imageId        : "ami-1234567",
        imageName      : "my_image"
      ])
    }
    0 * oortService._
    previousServerGroupMetadata == [
      name         : "application-v001",
      imageId      : "ami-1234567",
      imageName    : "my_image",
      cloudProvider: "aws"
    ]

    when: "previous server group does NOT exist"
    previousServerGroupMetadata = tagGenerator.getPreviousServerGroupFromCluster(
      "application", "account", "cluster", "aws", "us-west-2", "application-v002"
    )

    then: "no metadata should be returned"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "aws", "us-west-2", "NEWEST", "image", "true") >> {
      throw notFoundError
    }
    0 * oortService._ // when NEWEST is not found, no fallback is made to query for ANCESTOR
    previousServerGroupMetadata == null
  }

  def "should check NEWEST and ANCESTOR when constructing previous server group metadata"() {
    given:
    def tagGenerator = new SpinnakerMetadataServerGroupTagGenerator(oortService, retrySupport)

    when: "NEWEST != just created server group"
    def previousServerGroupMetadata = tagGenerator.getPreviousServerGroupFromCluster(
      "application", "account", "cluster", "titus", "us-west-2", "application-v002"
    )

    then: "previous server group == NEWEST"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "titus", "us-west-2", "NEWEST", "image", "true") >> {
      Calls.response( [
        serverGroupName: "application-v001",
        imageId        : "1234567",
        imageName      : "my_image"
      ])
    }
    0 * oortService._
    previousServerGroupMetadata == [
      name         : "application-v001",
      imageId      : "1234567",
      imageName    : "my_image",
      cloudProvider: "titus"
    ]

    when: "NEWEST == just created server group"
    previousServerGroupMetadata = tagGenerator.getPreviousServerGroupFromCluster(
      "application", "account", "cluster", "titus", "us-west-2", "application-v002"
    )

    then: "previous server group == ANCESTOR"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "titus", "us-west-2", "NEWEST", "image", "true") >> {
      Calls.response( [
        serverGroupName: "application-v002",
        imageId        : "1234567",
        imageName      : "my_image"
      ])
    }
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "titus", "us-west-2", "ANCESTOR", "image", "true") >> {
      Calls.response( [
        serverGroupName: "application-v001",
        imageId        : "1234567",
        imageName      : "my_image"
      ])
    }
    0 * oortService._
    previousServerGroupMetadata == [
      name         : "application-v001",
      imageId      : "1234567",
      imageName    : "my_image",
      cloudProvider: "titus"
    ]
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status, String message = "{ \"message\": \"arbitrary message\" }") {
    String url = "https://mort";
    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), message))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
