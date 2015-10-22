/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.Location
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class FindImageFromClusterTaskSpec extends Specification {

  @Subject
      task = new FindImageFromClusterTask()
  OortService oortService = Mock(OortService)

  def setup() {
    task.oortService = oortService
    task.objectMapper = new ObjectMapper()
  }

  @Unroll
  def "should output deployment details based on ImageSummary"() {
    given:
      def pipe = new Pipeline.Builder()
          .withApplication("contextAppName") // Should be ignored.
          .build()
      def stage = new PipelineStage(pipe, "findImage", [
          cloudProvider    : "cloudProvider",
          cluster          : "foo-test",
          account          : "test",
          selectionStrategy: "LARGEST",
          onlyEnabled      : "false",
          regions          : [location1.value, location2.value]
      ])

    when:
      def result = task.execute(stage)

    then:
      1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "cloudProvider", location1.value,
          "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> oortResponse1
      1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "cloudProvider", location2.value,
          "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> oortResponse2
      assertNorth result.globalOutputs?.deploymentDetails?.find { it.region == "north" } as Map
      assertSouth result.globalOutputs?.deploymentDetails?.find { it.region == "south" } as Map

    where:
      location1 = new Location(type: Location.Type.REGION, value: "north")
      location2 = new Location(type: Location.Type.REGION, value: "south")

      oortResponse1 = [
        serverGroupName:  "foo-test-v000",
        imageId: "ami-012",
        imageName: "ami-012-name",
        image: [ imageId: "ami-012", name: "ami-012-name", foo: "bar" ],
        buildInfo: [ job: "foo-build", buildNumber: 1 ]
      ]

      oortResponse2 = [
        serverGroupName:  "foo-test-v002",
        imageId: "ami-234",
        imageName: "ami-234-name",
        image: [ imageId: "ami-234", name: "ami-234-name", foo: "baz" ],
        buildInfo: [ job: "foo-build", buildNumber: 1 ]
      ]
  }

  private void assertNorth(Map details) {
    assert details != null
    assert details.sourceServerGroup == "foo-test-v000"
    assert details.ami == "ami-012"
    assert details.imageId == "ami-012"
    assert details.imageName == "ami-012-name"
    assert details.foo == "bar"
  }

  private void assertSouth(Map details) {
    assert details != null
    assert details.sourceServerGroup == "foo-test-v002"
    assert details.ami == "ami-234"
    assert details.imageId == "ami-234"
    assert details.imageName == "ami-234-name"
    assert details.foo == "baz"
  }

  def "should parse fail strategy error message"() {
    given:
      def stage = new PipelineStage(new Pipeline(), "whatever", [
          cloudProvider    : "cloudProvider",
          cluster          : "foo-test",
          account          : "test",
          selectionStrategy: "FAIL",
          onlyEnabled      : "false",
          zones          : [location.value]
      ])

      Response response = new Response("http://oort", 404, "NOT_FOUND", [], new TypedString(oortResponse))

    when:
      task.execute(stage.asImmutable())

    then:
      1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "cloudProvider", location.value,
          "FAIL", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> {
        throw new RetrofitError(null, null, response, null, null, null, null)
      }
      IllegalStateException ise = thrown()
      ise.message == "Multiple possible server groups present in ${location.value}".toString()

    where:
      location = new Location(type: Location.Type.ZONE, value: "north-pole-1a")
      oortResponse = """\
      {
        "error" : "target.fail.strategy",
        "message" : "target.fail.strategy",
        "status" : "NOT_FOUND"
      }
      """.stripIndent()
  }
}
