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
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class EnabledPipelineValidatorSpec extends Specification {

  def front50Service = Mock(Front50Service)

  @Subject
  def validator = new EnabledPipelineValidator(Optional.of(front50Service))

  def "allows one-off pipeline to run"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> []
    1 * front50Service.getPipelines(execution.application, false) >> []

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "allows enabled pipeline to run"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> [
    ]
    1 * front50Service.getPipelines(execution.application, false) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]
    0 * _

    notThrown(PipelineValidationFailed)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> {
      throw RetrofitError.httpError(
          "http://localhost",
          new Response("http://localhost", HTTP_NOT_FOUND, "Not Found", [], null),
          null,
          null
      )
    }
    1 * front50Service.getPipelines(execution.application, false) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]
    0 * _

    notThrown(PipelineValidationFailed)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]
    0 * _

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "prevents disabled pipeline from running"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> [
    ]
    1 * front50Service.getPipelines(execution.application, false) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ]
    0 * _

    thrown(EnabledPipelineValidator.PipelineIsDisabled)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ]
    0 * _

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
      trigger = new PipelineTrigger("pipeline", null, null, [:], [], [], false, false, true, pipeline {
      })
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
      trigger = new PipelineTrigger("pipeline", null, null, [:], [], [], false, false, true, pipeline {
      })
    }
  }

  def "doesn't choke on non-boolean strategy value"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipelineHistory(execution.pipelineConfigId, 1) >> [
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ]

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
      trigger = new DefaultTrigger("manual", null, "fzlem", [strategy: "kthxbye"])
    }
  }
}
