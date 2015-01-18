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
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification

class TargetReferenceSupportSpec extends Specification {

  ObjectMapper mapper = OrcaObjectMapper.DEFAULT
  OortService oort
  TargetReferenceSupport subject

  def pipeline = new Pipeline()

  def cluster = [serverGroups: [[
                                  name  : "kato-main-v000",
                                  region: "us-west-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v001",
                                  region: "us-west-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v001",
                                  region: "us-east-1",
                                  asg   : [
                                    minSize        : 5,
                                    maxSize        : 5,
                                    desiredCapacity: 5
                                  ]
                                ], [
                                  name  : "kato-main-v002",
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

  void "should resolve target reference appropriately"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      cluster    : "kato-main",
      target     : target,
      credentials: "prod"
    ]
    def stage = new PipelineStage(pipeline, "test", config)

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
    target         | regions                    | asgNames
    "current_asg"  | ["us-west-1", "us-east-1"] | ["kato-main-v001", "kato-main-v002"]
    "ancestor_asg" | ["us-west-1", "us-east-1"] | ["kato-main-v000", "kato-main-v001"]
  }

  void "should resolve exact target reference appropriately"() {
    setup:
    def config = [
      regions    : ["us-west-1", "us-east-1"],
      asgName    : "kato-main-v000",
      credentials: "prod"
    ]
    def stage = new PipelineStage(pipeline, "test", config)

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
}
