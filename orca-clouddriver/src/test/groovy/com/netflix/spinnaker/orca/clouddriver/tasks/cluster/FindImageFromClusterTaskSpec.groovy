/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.RegionCollector
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class FindImageFromClusterTaskSpec extends Specification {

  @Subject
  task = new FindImageFromClusterTask()
  OortService oortService = Mock(OortService)
  RegionCollector regionCollector = Mock(RegionCollector)

  def setup() {
    regionCollector.getRegionsFromChildStages(_ as Stage) >> { stage -> new HashSet<String>() }

    task.oortService = oortService
    task.objectMapper = new ObjectMapper()
    task.regionCollector = regionCollector
  }

  @Unroll
  def "should output deployment details based on ImageSummary"() {
    given:
    def pipe = pipeline {
      application = "contextAppName" // Should be ignored.
    }
    def stage = new Stage(pipe, "findImage", [
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
    assertNorth result.outputs?.deploymentDetails?.find {
      it.region == "north"
    } as Map
    assertSouth result.outputs?.deploymentDetails?.find {
      it.region == "south"
    } as Map

    where:
    location1 = new Location(type: Location.Type.REGION, value: "north")
    location2 = new Location(type: Location.Type.REGION, value: "south")

    oortResponse1 = [
      summaries: [[
                    serverGroupName: "foo-test-v000",
                    imageId        : "ami-012",
                    imageName      : "ami-012-name",
                    image          : [imageId: "ami-012", name: "ami-012-name", foo: "bar"],
                    buildInfo      : [job: "foo-build", buildNumber: 1]
                  ]]
    ]

    oortResponse2 = [
      summaries: [[
                    serverGroupName: "foo-test-v002",
                    imageId        : "ami-234",
                    imageName      : "ami-234-name",
                    image          : [imageId: "ami-234", name: "ami-234-name", foo: "baz"],
                    buildInfo      : [job: "foo-build", buildNumber: 1]
                  ]]
    ]
  }

  def "should be RUNNING if summary does not include imageId"() {
    given:
    def pipe = pipeline {
      application = "orca" // Should be ignored.
    }

    def stage = new Stage(pipe, "findImage", [
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
    result.status == ExecutionStatus.RUNNING

    where:
    location1 = new Location(type: Location.Type.REGION, value: "north")
    location2 = new Location(type: Location.Type.REGION, value: "south")

    oortResponse1 = [
      summaries: [[
                    serverGroupName: "foo-test-v000",
                    imageId        : "ami-012",
                    imageName      : "ami-012-name",
                    image          : [imageId: "ami-012", name: "ami-012-name", foo: "bar"],
                    buildInfo      : [job: "foo-build", buildNumber: 1]
                  ]]
    ]

    oortResponse2 = [
      summaries: [[
                    serverGroupName: "foo-test-v002"
                  ]]
    ]
  }

  private void assertNorth(Map details, Map expectOverrides = [:]) {
    assert details != null
    with(details) {
      sourceServerGroup == (expectOverrides.sourceServerGroup ?: "foo-test-v000")
      ami == (expectOverrides.ami ?: "ami-012")
      imageId == (expectOverrides.imageId ?: "ami-012")
      imageName == (expectOverrides.imageName ?: "ami-012-name")
      foo == (expectOverrides.foo ?: "bar")
    }
  }

  private void assertSouth(Map details, Map expectOverrides = [:]) {
    assert details != null
    with(details) {
      sourceServerGroup == (expectOverrides.sourceServerGroup ?: "foo-test-v002")
      ami == (expectOverrides.ami ?: "ami-234")
      imageId == (expectOverrides.imageId ?: "ami-234")
      imageName == (expectOverrides.imageName ?: "ami-234-name")
      foo == (expectOverrides.foo ?: "baz")
    }
  }

  def 'extractBaseImageNames should dedupe extraneous bakes'() {

    expect:
    task.extractBaseImageNames(names) == expected

    where:
    names                                                           | expected
    ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-ebs']  | ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-ebs'] as Set
    ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-ebs1'] | ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-ebs'] as Set
    ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-s3']   | ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-s3'] as Set
    ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-s4']   | ['foo-120.2-h180.ffea4c7-x86_64-20160218235358-trusty-pv-s4'] as Set
    ['foo-x86_64-201603232351']                                     | ['foo-x86_64-201603232351'] as Set
  }

  @Unroll
  def "should resolve images via find if not all regions exist in source server group"() {
    given:
    def pipe = pipeline {
      application = "contextAppName" // Should be ignored.
    }
    def stage = new Stage(pipe, "findImage", [
      resolveMissingLocations: true,
      cloudProvider          : cloudProvider,
      cluster                : "foo-test",
      account                : "test",
      selectionStrategy      : "LARGEST",
      onlyEnabled            : "false",
      regions                : [
        location1.value
        //Note: location2.value will come from regionCollectorResponse below
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getServerGroupSummary("foo", "test", "foo-test", cloudProvider, location1.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> oortResponse1
    findCalls * oortService.getServerGroupSummary("foo", "test", "foo-test", cloudProvider, location2.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> {
      throw RetrofitError.httpError("http://clouddriver", new Response("http://clouddriver", 404, 'Not Found', [], new TypedString("{}")), null, Map)
    }
    findCalls * oortService.findImage(cloudProvider, "ami-012-name-ebs*", "test", null, null) >> imageSearchResult
    findCalls * regionCollector.getRegionsFromChildStages(stage) >> regionCollectorResponse

    assertNorth(result.outputs?.deploymentDetails?.find {
      it.region == "north"
    } as Map, [imageName: "ami-012-name-ebs"])

    if (cloudProvider == "aws") {
      assertSouth(result.outputs?.deploymentDetails?.find {
        it.region == "south"
      } as Map, [sourceServerGroup: "foo-test", imageName: "ami-012-name-ebs1", foo: "bar"])
    } else {
      assert !result.outputs?.deploymentDetails?.any {
        it.region == "south"
      }
    }

    where:
    location1 = new Location(type: Location.Type.REGION, value: "north")
    location2 = new Location(type: Location.Type.REGION, value: "south")

    oortResponse1 = [
      summaries: [[
                    serverGroupName: "foo-test-v000",
                    imageId        : "ami-012",
                    imageName      : "ami-012-name-ebs",
                    image          : [imageId: "ami-012", name: "ami-012-name-ebs", foo: "bar"],
                    buildInfo      : [job: "foo-build", buildNumber: 1]
                  ]]
    ]

    regionCollectorResponse = [location2.value]

    imageSearchResult = [
      [
        imageName: "ami-012-name-ebs",
        amis     : [
          "north": ["ami-012"]
        ]
      ],
      [
        imageName: "ami-012-name-ebs1",
        amis     : [
          "south": ["ami-234"]
        ]
      ]
    ]

    cloudProvider || findCalls
    "aws" || 1
    "gcp" || 0
  }

  def "should resolve images via find if not all regions exist in source server group without build info"() {
    given:
    def pipe = pipeline {
      application = "contextAppName" // Should be ignored.
    }
    def stage = new Stage(pipe, "findImage", [
      resolveMissingLocations: true,
      cloudProvider          : "cloudProvider",
      cluster                : "foo-test",
      account                : "test",
      selectionStrategy      : "LARGEST",
      onlyEnabled            : "false",
      regions                : [location1.value, location2.value]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "cloudProvider", location1.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> oortResponse1
    1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "cloudProvider", location2.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> {
      throw RetrofitError.httpError("http://clouddriver", new Response("http://clouddriver", 404, 'Not Found', [], new TypedString("{}")), null, Map)
    }
    1 * oortService.findImage("cloudProvider", "ami-012-name-ebs*", "test", null, null) >> imageSearchResult
    assertNorth(result.outputs?.deploymentDetails?.find {
      it.region == "north"
    } as Map, [imageName: "ami-012-name-ebs"])
    assertSouth(result.outputs?.deploymentDetails?.find {
      it.region == "south"
    } as Map, [sourceServerGroup: "foo-test", imageName: "ami-012-name-ebs1", foo: "bar"])

    where:
    location1 = new Location(type: Location.Type.REGION, value: "north")
    location2 = new Location(type: Location.Type.REGION, value: "south")

    oortResponse1 = [
      summaries: [[
                    serverGroupName: "foo-test-v000",
                    imageId        : "ami-012",
                    imageName      : "ami-012-name-ebs",
                    image          : [imageId: "ami-012", name: "ami-012-name-ebs", foo: "bar"]
                  ]]
    ]

    imageSearchResult = [
      [
        imageName: "ami-012-name-ebs",
        amis     : [
          "north": ["ami-012"]
        ]
      ],
      [
        imageName: "ami-012-name-ebs1",
        amis     : [
          "south": ["ami-234"]
        ]
      ]
    ]
  }

  def "should fallback to look up image from default bake account for AWS if not found in target account"() {
    given:
    task.defaultBakeAccount = 'bakery'
    def pipe = pipeline {
      application = "contextAppName" // Should be ignored.
    }
    def stage = new Stage(pipe, "findImage", [
      resolveMissingLocations: true,
      cloudProvider          : "aws",
      cluster                : "foo-test",
      account                : "test",
      selectionStrategy      : "LARGEST",
      onlyEnabled            : "false",
      regions                : [location1.value, location2.value]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "aws", location1.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> oortResponse1
    1 * oortService.getServerGroupSummary("foo", "test", "foo-test", "aws", location2.value,
      "LARGEST", FindImageFromClusterTask.SUMMARY_TYPE, false.toString()) >> {
      throw RetrofitError.httpError("http://clouddriver", new Response("http://clouddriver", 404, 'Not Found', [], new TypedString("{}")), null, Map)
    }
    1 * oortService.findImage("aws", "ami-012-name-ebs*", "test", null, null) >> null
    1 * oortService.findImage("aws", "ami-012-name-ebs*", "bakery", null, null) >> imageSearchResult
    assertNorth(result.outputs?.deploymentDetails?.find {
      it.region == "north"
    } as Map, [imageName: "ami-012-name-ebs"])
    assertSouth(result.outputs?.deploymentDetails?.find {
      it.region == "south"
    } as Map, [sourceServerGroup: "foo-test", imageName: "ami-012-name-ebs1", foo: "bar"])

    where:
    location1 = new Location(type: Location.Type.REGION, value: "north")
    location2 = new Location(type: Location.Type.REGION, value: "south")

    oortResponse1 = [
      summaries: [[
                    serverGroupName: "foo-test-v000",
                    imageId        : "ami-012",
                    imageName      : "ami-012-name-ebs",
                    image          : [imageId: "ami-012", name: "ami-012-name-ebs", foo: "bar"]
                  ]]
    ]

    imageSearchResult = [
      [
        imageName: "ami-012-name-ebs",
        amis     : [
          "north": ["ami-012"]
        ]
      ],
      [
        imageName: "ami-012-name-ebs1",
        amis     : [
          "south": ["ami-234"]
        ]
      ]
    ]

  }

  def "should parse fail strategy error message"() {
    given:
    def pipe = pipeline {
      application = "orca" // Should be ignored.
    }

    def stage = new Stage(pipe, "whatever", [
      cloudProvider    : "cloudProvider",
      cluster          : "foo-test",
      account          : "test",
      selectionStrategy: "FAIL",
      onlyEnabled      : "false",
      zones            : [location.value]
    ])

    Response response = new Response("http://oort", 404, "NOT_FOUND", [], new TypedString(oortResponse))

    when:
    task.execute(stage)

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

  @Unroll
  'cluster with name "#cluster" and moniker "#moniker" should have application name "#expected"'() {
    given:
    def pipe = pipeline {
      application = "orca" // Should be ignored.
    }
    def stage = new Stage(pipe, 'findImageFromCluster', [
      cluster: cluster,
      moniker: moniker,
    ])
    when:
    FindImageFromClusterTask.FindImageConfiguration config = stage.mapTo(FindImageFromClusterTask.FindImageConfiguration)

    then:
    config.getApplication() == expected

    where:
    cluster       | moniker            | expected
    'clustername' | ['app': 'appname'] | 'appname'
    'app-stack'   | null               | 'app'

  }
}
