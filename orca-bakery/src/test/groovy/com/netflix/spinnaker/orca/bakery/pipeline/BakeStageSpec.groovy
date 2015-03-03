/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import groovy.time.TimeCategory
import spock.lang.Specification
import spock.lang.Unroll

class BakeStageSpec extends Specification {
  @Unroll
  def "should build contexts corresponding to locally specified bake region and all target deploy regions"() {
    given:
    def pipelineBuilder = new Pipeline().builder()
    deployAvailabilityZones?.each {
      pipelineBuilder = pipelineBuilder.withStage("deploy", "Deploy!", [cluster: [availabilityZones: it]])
    }
    def pipeline = pipelineBuilder.build()

    def bakeStage = new PipelineStage(pipeline, "bake", "Bake!", bakeStageContext)
    def builder = Spy(BakeStage, {
      (0..1) * now() >> {
        use([TimeCategory]) {
          return new Date(0) + 1.hour + 15.minutes
        }
      }
    })

    when:
    def parallelContexts = builder.parallelContexts(bakeStage)

    then:
    parallelContexts == expectedParallelContexts

    where:
    bakeStageContext                       | deployAvailabilityZones                || expectedParallelContexts
    [:]                                    | [["us-west-1": []], ["us-west-2": []]] || buildContexts("197001010115", "us-west-1", "us-west-2")
    [region: "us-east-1"]                  | [["us-west-1": []]]                    || buildContexts("197001010115", "us-east-1", "us-west-1")
    [region: "us-east-1"]                  | []                                     || buildContexts("197001010115", "us-east-1")
    [region: "us-east-1"]                  | null                                   || buildContexts("197001010115", "us-east-1")
    [region: "us-east-1", amiSuffix: ""]   | null                                   || buildContexts("197001010115", "us-east-1")
    [region: "us-east-1", amiSuffix: "--"] | null                                   || buildContexts("--", "us-east-1")
  }

  def "should include per-region stage contexts as global deployment details"() {
    given:
    def pipeline = Pipeline.builder()
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 1])
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 2])
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 3])
      .build()

    when:
    def taskResult = new BakeStage().completeParallel().execute(new PipelineStage(pipeline, "bake"))

    then:
    taskResult.globalOutputs == [
      deploymentDetails: [
        ["ami": 1], ["ami": 2], ["ami": 3]
      ]
    ]
  }

  private static List<Map> buildContexts(String amiSuffix, String... regions) {
    return regions.collect {
      [amiSuffix: amiSuffix, type: BakeStage.MAYO_CONFIG_TYPE, "region": it, name: "Bake in ${it}"]
    }
  }
}
