/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Unroll

class TargetReferenceSupportSpec extends Specification {

  ObjectMapper mapper = OrcaObjectMapper.newInstance()
  OortService oort
  TargetReferenceSupport subject

  def pipeline = new Pipeline()

  def cluster = [serverGroups: [
                                [
                                  name  : "kato-main-v999",
                                  createdTime: 0,
                                  region: "us-west-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v000",
                                  createdTime: 1,
                                  region: "us-west-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v001",
                                  createdTime: 2,
                                  region: "us-west-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v001",
                                  createdTime: 2,
                                  region: "us-east-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v002",
                                  createdTime: 3,
                                  region: "us-east-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ]]]

  def setup() {
    oort = Mock(OortService)
    subject = new TargetReferenceSupport(mapper: mapper, oort: oort)
  }

  @Unroll
  void "should resolve target (#target) reference appropriately"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      cluster    : "kato-main",
      target     : target,
      credentials: "prod"
    ]
    def stage = new Stage<>(pipeline, type, config)

    when:
    def targets = subject.getTargetAsgReferences(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    2 == targets.size()
    targets*.region == regions
    targets*.asg.name == asgNames

    where:
    target                 | type                       | regions                    | asgNames
    "current_asg"          | "test"                     | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v002"]
    "ancestor_asg"         | "test"                     | ["us-west-1", "us-east-1"] | ["kato-main-v000", "kato-main-v001"]
    "oldest_asg_dynamic"   | "determineTargetReference" | ["us-west-1", "us-east-1"] | ["kato-main-v999", "kato-main-v001"]
    "current_asg_dynamic"  | "determineTargetReference" | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v002"]
    "ancestor_asg_dynamic" | "determineTargetReference" | ["us-west-1", "us-east-1"] | ["kato-main-v000", "kato-main-v001"]

  }

  void "should throw exception when less than two ASGs are found and target is ancestor_asg"() {
    setup:
    def config = [
      regions     : ["us-east-1"],
      cluster     : "kato-main",
      target      : "ancestor_asg",
      credentials : "prod"
    ]
    def stage = new Stage<>(pipeline, "test", config)
    def response = mapper.writeValueAsBytes(
      [serverGroups: [[
        name  : "kato-main-v001",
        createdTime: 2,
        region: "us-east-1",
          asg   : [
            minSize        : 5,
            maxSize        : 5,
            desiredCapacity: 5
          ]
        ]
      ]]
    )

    when:
    subject.getTargetAsgReferences(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      new Response("foo", 200, "ok", [], new TypedByteArray("application/json", response))
    }
    thrown TargetReferenceNotFoundException
  }

  @Unroll
  void "should throw TargetReferenceNotFoundException when no ASGs are found and target is #target"() {
    setup:
    def config = [
      regions     : ["us-east-1"],
      cluster     : "kato-main",
      target      : target,
      credentials : "prod"
    ]
    def stage = new Stage<>(pipeline, "test", config)
    def response = mapper.writeValueAsBytes([serverGroups: []])

    when:
    subject.getTargetAsgReferences(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      new Response("foo", 200, "ok", [], new TypedByteArray("application/json", response))
    }
    thrown TargetReferenceNotFoundException

    where:
    target << ["ancestor_asg", "current_asg"]
  }

  @Unroll
  void "should look up cluster info for dynamic target (#target) only on DetermineTargetReferenceStage"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      cluster    : "kato-main",
      target     : target,
      credentials: "prod"
    ]
    def stage = new Stage<>(pipeline, type, config)

    when:
    subject.getTargetAsgReferences(stage)

    then:
    oortCalls * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }

    where:
    target                 | type                       | oortCalls
    "current_asg_dynamic"  | "determineTargetReference" | 1
    "ancestor_asg_dynamic" | "determineTargetReference" | 1
    "oldest_asg_dynamic"   | "determineTargetReference" | 1
    "current_asg_dynamic"  | "test"                     | 0
    "ancestor_asg_dynamic" | "test"                     | 0
    "oldest_asg_dynamic"   | "test"                     | 0
  }

  @Unroll
  void "should resolve target (#target) from upstream stage when dynamically bound"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      cluster    : "kato-main",
      target     : target,
      credentials: "prod"
    ]

    def upstreamTargets = [
      targetReferences: [
        new TargetReference(region: "us-west-1", cluster: "kato-main", asg: [ name: "kato-main-v001"]),
        new TargetReference(region: "us-east-1", cluster: "kato-main", asg: [ name: "kato-main-v000"])
      ]
    ]


    def rootStage = new Stage<>(pipeline, "root", config)

    def stage = new Stage<>(pipeline, "test", config)
    stage.parentStageId = rootStage.id

    def determineTargetStage = new Stage<>(pipeline, DetermineTargetReferenceStage.PIPELINE_CONFIG_TYPE, upstreamTargets)
    determineTargetStage.parentStageId = rootStage.id

    pipeline.stages = [rootStage, stage, determineTargetStage]

    when:
    def targets = subject.getTargetAsgReferences(stage)

    then:
    0 * oort.getCluster(_, _, _, _)
    2 == targets.size()
    targets*.region == regions
    targets*.asg.name == asgNames

    where:
    target                 | regions                    | asgNames
    "current_asg_dynamic"  | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v000"]
    "ancestor_asg_dynamic" | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v000"]
    "oldest_asg_dynamic"   | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v000"]

  }

  void "should resolve exact target reference appropriately"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      asgName    : "kato-main-v000",
      credentials: "prod"
    ]
    def stage = new Stage<>(pipeline, "test", config)

    when:
    def targets = subject.getTargetAsgReferences(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    1 == targets.size()
    targets*.region == ["us-west-1"]
    targets*.asg.name == ["kato-main-v000"]
  }

  void "should throw TargetReferenceNotFoundException when target reference not found or does not contain an ASG"() {
    setup:
    def config = [
        regions    : ["us-west-1", "us-east-1"],
        asgName    : "kato-main-v000",
        credentials: "prod"
    ]
    def stage = new Stage<>(pipeline, "test", config)

    when:
    subject.getDynamicallyBoundTargetAsgReference(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      throw new RetrofitError(null, null, new Response("http://clouddriver", 404, "null", [], null), null, null, null, null)
    }
    thrown TargetReferenceNotFoundException
  }

  void "should throw RetrofitError when status is not 404"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      asgName    : "kato-main-v000",
      credentials: "prod"
    ]
    def stage = new Stage<>(pipeline, "test", config)

    when:
    subject.getDynamicallyBoundTargetAsgReference(stage)

    then:
    1 * oort.getCluster("kato", "prod", "kato-main", "aws") >> {
      throw new RetrofitError(null, null, new Response("http://clouddriver", 429, "null", [], null), null, null, null, null)
    }
    thrown RetrofitError
  }
}
