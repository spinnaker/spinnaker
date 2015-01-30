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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Unroll

class DeployStrategyStageSpec extends Specification {
  @Unroll
  void "should include freeFormDetails when building cluster name"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "deploy",
      [
        application    : application,
        stack          : stack,
        freeFormDetails: freeFormDetails
      ]
    )

    expect:
    stage.mapTo(DeployStrategyStage.StageData).getCluster() == cluster

    where:
    application | stack        | freeFormDetails || cluster
    "myapp"     | "prestaging" | "freeform"      || "myapp-prestaging-freeform"
    "myapp"     | "prestaging" | null            || "myapp-prestaging"
    "myapp"     | null         | "freeform"      || "myapp--freeform"
    "myapp"     | null         | null            || "myapp"
  }
}
