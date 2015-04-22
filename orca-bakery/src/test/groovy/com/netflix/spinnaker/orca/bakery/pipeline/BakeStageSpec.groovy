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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
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
      pipelineBuilder = pipelineBuilder.withStage("deploy", "Deploy!", it)
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
    bakeStageContext                       | deployAvailabilityZones                       || expectedParallelContexts
    [:]                                    | deployAz("cluster", "us-west-1", "us-west-2") || expectedContexts("197001010115", "us-west-1", "us-west-2")
    [region: "us-east-1"]                  | deployAz("cluster", "us-west-1")              || expectedContexts("197001010115", "us-east-1", "us-west-1")
    [:]                                    | deployAz("clusters", "us-west-1")             || expectedContexts("197001010115", "us-west-1")
    [region: "us-east-1"]                  | deployAz("clusters", "us-west-1")             || expectedContexts("197001010115", "us-east-1", "us-west-1")
    [region: "us-east-1"]                  | []                                            || expectedContexts("197001010115", "us-east-1")
    [region: "us-east-1"]                  | null                                          || expectedContexts("197001010115", "us-east-1")
    [region: "us-east-1", amiSuffix: ""]   | null                                          || expectedContexts("197001010115", "us-east-1")
    [region: "us-east-1", amiSuffix: "--"] | null                                          || expectedContexts("--", "us-east-1")
    [region: "global"]                     | deployAz("clusters", "us-west-1")             || expectedContexts("197001010115", "global")
  }

  def "should include per-region stage contexts as global deployment details"() {
    given:
    def pipeline = Pipeline.builder()
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 1])
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 2])
      .withStage(BakeStage.MAYO_CONFIG_TYPE, "Bake", ["ami": 3])
      .build()

    def pipelineStage = new PipelineStage(pipeline, "bake")
    pipeline.stages.each {
      it.status = ExecutionStatus.RUNNING
      it.parentStageId = pipelineStage.parentStageId
      ((AbstractStage)it).id = pipelineStage.parentStageId
    }

    when:
    def taskResult = new BakeStage().completeParallel().execute(pipelineStage)

    then:
    taskResult.globalOutputs == [
      deploymentDetails: [
        ["ami": 1], ["ami": 2], ["ami": 3]
      ]
    ]
  }

  @Unroll
  def "should return a different stage name when parallel flows are present"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "type", stageName, [:])

    expect:
    new BakeStage().parallelStageName(stage, hasParallelFlows) == expectedStageName

    where:
    stageName | hasParallelFlows || expectedStageName
    "Default" | false            || "Default"
    "Default" | true             || "Multi-region Bake"
  }

  private static List<Map> deployAz(String prefix, String... regions) {
    if (prefix == "clusters") {
      return [[clusters: regions.collect { [availabilityZones: [(it): []]] }]]
    }

    return regions.collect {
      if (prefix == "cluster") {
        return [cluster: [availabilityZones: [(it): []]]]
      }
      return [availabilityZones: [(it): []]]
    }
  }

  private static List<Map> expectedContexts(String amiSuffix, String... regions) {
    return regions.collect {
      [amiSuffix: amiSuffix, type: BakeStage.MAYO_CONFIG_TYPE, "region": it, name: "Bake in ${it}"]
    }
  }
}
