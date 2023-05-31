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

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.PipelineValidator.PipelineValidationFailed
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class EnabledPipelineValidatorSpec extends Specification {

  def front50Service = Mock(Front50Service)

  @Subject
  def validator = new EnabledPipelineValidator(Optional.of(front50Service))

  def "allows one-off pipeline to run"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw notFoundError() }
    1 * front50Service.getPipelines(execution.application, false) >> Calls.response([])
    0 * front50Service._

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "ignores 500 responses from getPipeline"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw makeSpinnakerHttpException(500) }
    1 * front50Service.getPipelines(execution.application, false) >> Calls.response([])
    0 * front50Service._

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "fails when getPipelines responds with 500"() {
    given:
    SpinnakerHttpException spinnakerHttpException = makeSpinnakerHttpException(500)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw notFoundError() }
    1 * front50Service.getPipelines(execution.application, false) >> { throw spinnakerHttpException }
    0 * front50Service._

    // checkRunnable is documented to throw a PipelineValidationFailed exception
    // if the pipeline can not run.  In this case, we're not sure that the
    // pipeline can not run.  So, is it better to swallow this exception?  I'd
    // say it's better to allow it to bubble up and let some higher level code
    // decide how to handle it.
    def e = thrown(SpinnakerHttpException)
    e == spinnakerHttpException

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
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw notFoundError() }
    1 * front50Service.getPipelines(execution.application, false) >> Calls.response([
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ])
    0 * _

    notThrown(PipelineValidationFailed)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw notFoundError() }
    1 * front50Service.getPipelines(execution.application, false) >> Calls.response([
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ])
    0 * _

    notThrown(PipelineValidationFailed)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> Calls.response(
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false])
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
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> { throw notFoundError() }
    1 * front50Service.getPipelines(execution.application, false) >> Calls.response([
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ])
    0 * _

    thrown(EnabledPipelineValidator.PipelineIsDisabled)

    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> Calls.response(
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true])
    0 * _

    thrown(EnabledPipelineValidator.PipelineIsDisabled)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
    }
  }

  def "allows enabled strategy to run"() {
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getStrategies(execution.application) >> Calls.response([
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false]
    ])
    0 * front50Service._

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
    when:
    validator.checkRunnable(execution)

    then:
    1 * front50Service.getStrategies(execution.application) >> Calls.response([
        [id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: true]
    ])
    0 * front50Service._

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
    1 * front50Service.getPipeline(execution.pipelineConfigId) >> Calls.response([id: execution.pipelineConfigId, application: execution.application, name: "whatever", disabled: false])
    0 * front50Service._

    notThrown(PipelineValidationFailed)

    where:
    execution = pipeline {
      application = "whatever"
      pipelineConfigId = "1337"
      trigger = new DefaultTrigger("manual", null, "fzlem", [strategy: "kthxbye"])
    }
  }

  def notFoundError() {
    makeSpinnakerHttpException(404)
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status, String message = "{ \"message\": \"arbitrary message\" }") {
    String url = "https://front50";
    Response retrofit2Response =
        Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), message))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
