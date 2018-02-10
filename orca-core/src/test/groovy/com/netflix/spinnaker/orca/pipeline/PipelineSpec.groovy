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

import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

@Unroll
class PipelineSpec extends Specification {

  @Subject
    pipeline = pipeline {
      trigger = new JenkinsTrigger("master", "SPINNAKER-build-job", 1, null)
      stage { type = "stage1" }
      stage { type = "stage2" }
      stage { type = "stage3" }
    }

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
    pipeline.trigger.job == "SPINNAKER-build-job"
    pipeline.trigger.buildNumber == 1
  }
}
