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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.TaskNode.task

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
    def determineSourceServerGroupTask = task("determineSourceServerGroup", DetermineSourceServerGroupTask)
    def determineHealthProvidersTask = task("determineHealthProviders", DetermineHealthProvidersTask)
    def basicTask = task("basic", Task)

    AbstractDeployStrategyStage testStage = Spy(AbstractDeployStrategyStage)
    testStage.with {
      strategies = [noStrat, aStrat, bStrat, cStrat]
      noStrategy = noStrat
    }

    Stage stage = new Stage(Execution.newPipeline("orca"), "whatever", [strategy: specifiedStrategy])

    when:
    def tasks = testStage.buildTaskGraph(stage)

    then:
    1 * testStage.basicTasks(*_) >> [basicTask]
    tasks*.name == [determineSourceServerGroupTask.name, determineHealthProvidersTask.name, basicTask.name]

    where:
    specifiedStrategy | strategyObject
    "doesNotExist"    | noStrat
    "none"            | noStrat
    "a"               | aStrat
    "B"               | bStrat
  }
}
