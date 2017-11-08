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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.time.TimeCategory
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class BakeStageSpec extends Specification {
  @Unroll
  def "should build contexts corresponding to locally specified bake region and all target deploy regions"() {
    given:
    def pipeline = pipeline {
      deployAvailabilityZones?.each { zones ->
        stage {
          type = "deploy"
          name = "Deploy!"
          context = zones
        }
      }
    }

    def bakeStage = new Stage(pipeline, "bake", "Bake!", bakeStageContext)
    def builder = Spy(BakeStage, {
      (0..1) * now() >> {
        use([TimeCategory]) {
          return new Date(0) + 1.hour + 15.minutes + 12.seconds
        }
      }
    })

    when:
    def parallelContexts = builder.parallelContexts(bakeStage)

    then:
    parallelContexts == expectedParallelContexts

    where:
    bakeStageContext                                                        | deployAvailabilityZones                                                            || expectedParallelContexts
    [cloudProviderType: "aws"]                                              | deployAz("aws", "cluster", "us-west-1") + deployAz("aws", "cluster", "us-west-2")  || expectedContexts("aws", "19700101011512", "us-west-1", "us-west-2")
    [cloudProviderType: "aws", region: "us-east-1"]                         | deployAz("aws", "cluster", "us-west-1")                                            || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws"]                                              | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1"]                         | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1"]                         | []                                                                                 || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1"]                         | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1"]                         | deployAz("aws", "clusters", "us-east-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1", amiSuffix: ""]          | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1", amiSuffix: "--"]        | null                                                                               || expectedContexts("aws", "--", "us-east-1")
    [cloudProviderType: "aws", region: "global"]                            | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "global")
    [cloudProviderType: "aws", region: "us-east-1", regions: ["us-west-1"]] | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1", regions: []]            | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"]]         | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1", regions: null]          | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"]]         | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws"]                                              | deployAz("aws", "cluster", "us-west-1") + deployAz("gce", "cluster", "us-west1")   || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "gce", region: "global"]                            | deployAz("aws", "cluster", "us-west-1")                                            || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "gce", region: "global"]                            | deployAz("gce", "cluster", "us-west1")                                             || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "aws"]                                              | deployAz("aws", "clusters", "us-west-1") + deployAz("gce", "clusters", "us-west1") || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "gce", region: "global"]                            | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "gce", region: "global"]                            | deployAz("gce", "clusters", "us-west1")                                            || expectedContexts("gce", "19700101011512", "global")
  }

  def "should include per-region stage contexts as global deployment details"() {
    given:
    def pipeline = pipeline {
      stage {
        id = "1"
        type = "bake"
        context = [
          "region": "us-east-1",
          "regions": ["us-east-1", "us-west-2", "eu-east-1"]
        ]
        status = ExecutionStatus.RUNNING
      }
    }

    def bakeStage = pipeline.stageById("1")
    def parallelStages = new BakeStage().parallelStages(bakeStage)
    parallelStages.eachWithIndex { it, idx -> it.context.ami = idx + 1 }
    pipeline.stages.addAll(parallelStages)

    when:
    def taskResult = new BakeStage.CompleteParallelBakeTask().execute(pipeline.stageById("1"))

    then:
    with(taskResult.outputs) {
      deploymentDetails[0].ami == 1
      deploymentDetails[1].ami == 2
      deploymentDetails[2].ami == 3
    }
  }

  private
  static List<Map> deployAz(String cloudProvider, String prefix, String... regions) {
    if (prefix == "clusters") {
      return [[clusters: regions.collect {
        [cloudProvider: cloudProvider, availabilityZones: [(it): []]]
      }]]
    }

    return regions.collect {
      if (prefix == "cluster") {
        return [cluster: [cloudProvider: cloudProvider, availabilityZones: [(it): []]]]
      }
      return [cloudProvider: cloudProvider, availabilityZones: [(it): []]]
    }
  }

  private
  static List<Map> expectedContexts(String cloudProvider, String amiSuffix, String... regions) {
    return regions.collect {
      [cloudProviderType: cloudProvider, amiSuffix: amiSuffix, type: BakeStage.PIPELINE_CONFIG_TYPE, "region": it, name: "Bake in ${it}"]
    }
  }
}
