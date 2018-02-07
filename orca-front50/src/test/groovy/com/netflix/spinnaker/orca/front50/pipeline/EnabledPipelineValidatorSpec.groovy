/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.pipeline

import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.PipelineValidator.PipelineValidationFailed
import com.netflix.spinnaker.orca.pipeline.model.ManualTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class EnabledPipelineValidatorSpec extends Specification {

  def front50Service = Stub(Front50Service)
  @Subject
  def validator = new EnabledPipelineValidator(Optional.of(front50Service))

  def "allows one-off pipeline to run"() {
    given:
    front50Service.getPipelines(execution.application, false) >> []

    when:
    validator.checkRunnable(execution)

    then:
    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "allows enabled pipeline to run"() {
    given:
    front50Service.getPipelines(execution.application, false) >> [
      [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]

    when:
    validator.checkRunnable(execution)

    then:
    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "prevents disabled pipeline from running"() {
    given:
    front50Service.getPipelines(execution.application, false) >> [
      [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ]

    when:
    validator.checkRunnable(execution)

    then:
    thrown(EnabledPipelineValidator.PipelineIsDisabled)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "allows enabled strategy to run"() {
    given:
    front50Service.getStrategies(execution.application) >> [
      [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]

    when:
    validator.checkRunnable(execution)

    then:
    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
      trigger = new PipelineTrigger(pipeline {}, [strategy: true])
    }
  }

  def "prevents disabled strategy from running"() {
    given:
    front50Service.getStrategies(execution.application) >> [
      [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ]

    when:
    validator.checkRunnable(execution)

    then:
    thrown(EnabledPipelineValidator.PipelineIsDisabled)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
      trigger = new PipelineTrigger(pipeline {}, [strategy: true])
    }
  }

  def "doesn't choke on non-boolean strategy value"() {
    given:
    front50Service.getPipelines(execution.application, false) >> [
      [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]

    when:
    validator.checkRunnable(execution)

    then:
    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
      trigger = new ManualTrigger(null, "fzlem", [strategy: "kthxbye"], [], [])
    }
  }
}
