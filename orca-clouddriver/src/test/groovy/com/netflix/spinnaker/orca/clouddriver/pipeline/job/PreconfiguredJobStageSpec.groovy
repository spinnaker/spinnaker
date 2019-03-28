/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job

import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageParameter
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.clouddriver.config.KubernetesPreconfiguredJobProperties
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class PreconfiguredJobStageSpec extends Specification {

  def "should should replace properties in context"() {
    given:
    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [
          preconfiguredJobProperties
        ]
      }
    }

    def stage = stage {
      type = stageName
      context = stageContext
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    stage.getContext().get(expectedField) == expectedValue

    where:
    expectedField   | expectedValue   | stageName | stageContext                                                              | preconfiguredJobProperties
    "cloudProvider" | "kubernetes"    | "testJob" | [account: "test-account"]                                                 | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [], cloudProvider: "kubernetes")
    "cloudProvider" | "titus"         | "testJob" | [account: "test-account"]                                                 | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [new PreconfiguredJobStageParameter(mapping: "cloudProvider", defaultValue: "titus")], cloudProvider: "kubernetes")
    "cloudProvider" | "somethingElse" | "testJob" | [account: "test-account", parameters: ["cloudProvider": "somethingElse"]] | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [new PreconfiguredJobStageParameter(mapping: "cloudProvider", defaultValue: "titus", "name": "cloudProvider")], cloudProvider: "kubernetes")


  }
}
