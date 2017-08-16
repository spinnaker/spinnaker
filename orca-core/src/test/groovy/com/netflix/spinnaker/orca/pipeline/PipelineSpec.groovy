/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL

@Unroll
class PipelineSpec extends Specification {

  @Subject
    pipeline = Pipeline.builder()
      .withTrigger(name: "SPINNAKER-build-job", lastBuildLabel: 1)
      .withStage("stage1")
      .withStage("stage2")
      .withStage("stage3")
      .build()

  void setup() {
    pipeline.stages.findAll { it.tasks.isEmpty() }.each {
      // ensure each stage has at least one task (otherwise it will get skipped when calculating pipeline status)
      it.tasks << new Task()
    }
  }

  def "a v2 pipeline's status is 'executionStatus'"() {
    when:
    pipeline.status = RUNNING
    pipeline.stages[0].status = TERMINAL
    pipeline.stages[1].status = TERMINAL
    pipeline.stages[2].status = TERMINAL

    then:
    pipeline.status == RUNNING
  }

  def "trigger is properly build into the pipeline"() {
    expect:
    pipeline.trigger.name == "SPINNAKER-build-job" && pipeline.trigger.lastBuildLabel == 1
  }
}
