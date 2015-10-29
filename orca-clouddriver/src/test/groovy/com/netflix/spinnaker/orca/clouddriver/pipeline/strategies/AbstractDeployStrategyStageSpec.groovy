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

package com.netflix.spinnaker.orca.clouddriver.pipeline.strategies

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class AbstractDeployStrategyStageSpec extends Specification {

  @Shared
  Strategy aStrat = Stub(Strategy) { getName() >> "a" }
  @Shared
  Strategy bStrat = Stub(Strategy) { getName() >> "b" }
  @Shared
  Strategy cStrat = Stub(Strategy) { getName() >> "c" }
  @Shared
  NoStrategy noStrat = new NoStrategy()

  @Unroll
  def "should compose list of steps"() {
    given:
      // Step mocks
      Step mockDSSGStep = Stub(Step) { getName() >> "determineSourceServerGroup" }
      Step mockSupportStep = Stub(Step) { getName() >> "testSupportStep" }

      AbstractDeployStrategyStage testStage = Spy(AbstractDeployStrategyStage)
      testStage.with {
        strategies = [noStrat, aStrat, bStrat, cStrat]
        noStrategy = noStrat
      }

      Stage stage = new PipelineStage(new Pipeline(), "whatever", [strategy: specifiedStrategy])

    when:
      def steps = testStage.buildSteps(stage)

    then:
      // The actual goings on in the buildStep method are not relevant here, so just replace it.
      1 * testStage.buildStep(*_) >> mockDSSGStep
      1 * testStage.basicSteps(*_) >> [mockSupportStep]
      steps
      steps.size() == 2
      steps[0] == mockDSSGStep
      steps[1] == mockSupportStep

    where:
      specifiedStrategy | strategyObject
      "doesNotExist"    | noStrat
      "none"            | noStrat
      "a"               | aStrat
      "B"               | bStrat

  }

}
