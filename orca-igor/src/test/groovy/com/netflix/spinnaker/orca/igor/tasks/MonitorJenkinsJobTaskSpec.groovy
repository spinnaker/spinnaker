/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorJenkinsJobTaskSpec extends Specification {
  def executionRepository = Mock(ExecutionRepository)
  def artifactResolver = new ArtifactResolver(new ObjectMapper(), executionRepository)

  @Subject
  MonitorJenkinsJobTask task = new MonitorJenkinsJobTask()

  @Shared
  def pipeline = Execution.newPipeline("orca")

  @Unroll
  def "should return #taskStatus if job is #jobState"() {
    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: jobState]
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    jobState   | taskStatus
    'ABORTED'  | ExecutionStatus.CANCELED
    'FAILURE'  | ExecutionStatus.TERMINAL
    'SUCCESS'  | ExecutionStatus.SUCCEEDED
    'UNSTABLE' | ExecutionStatus.TERMINAL
    null       | ExecutionStatus.RUNNING
    'UNKNOWN'  | ExecutionStatus.RUNNING
  }

  @Unroll
  def "should ignore job state when build is running"() {
    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: running]
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    running | taskStatus
    true    | ExecutionStatus.RUNNING
    'true'  | ExecutionStatus.RUNNING
    null    | ExecutionStatus.SUCCEEDED
    false   | ExecutionStatus.SUCCEEDED
    'false' | ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "should ignore job state when build is building"() {
    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', building: state]
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    state   | taskStatus
    true    | ExecutionStatus.RUNNING
    'true'  | ExecutionStatus.RUNNING
    null    | ExecutionStatus.SUCCEEDED
    false   | ExecutionStatus.SUCCEEDED
    'false' | ExecutionStatus.SUCCEEDED
  }

  def "should return running status if igor call 404/500/503's"() {
    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4])

    and:
    def exception = Stub(RetrofitError) {
      getResponse() >> new Response('', httpStatus, '', [], null)
    }

    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> { throw exception }
    }

    when:
    def result = null
    def thrownException = null
    try {
      result = task.execute(stage)
    } catch (RetrofitError e) {
      thrownException = e
    }

    then:
    thrownException ? thrownException == exception : result.status == expectedExecutionStatus

    where:
    httpStatus || expectedExecutionStatus
    404        || ExecutionStatus.RUNNING
    500        || ExecutionStatus.RUNNING
    503        || ExecutionStatus.RUNNING
    400        || null
  }

  def "retrieves values from a property file if specified"() {

    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4, propertyFile: "sample.properties"])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: false]
      getPropertyFile(stage.context.buildNumber, stage.context.propertyFile, stage.context.master, stage.context.job) >> [val1: "one", val2: "two"]
    }
    task.retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    when:
    TaskResult result = task.execute(stage)

    then:
    result.context.val1 == 'one'
    result.context.val2 == 'two'

  }

  def "retrieves complex from a property file"() {

    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4, propertyFile: "sample.properties"])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: false]
      getPropertyFile(stage.context.buildNumber, stage.context.propertyFile, stage.context.master, stage.context.job) >>
        [val1: "one", val2: [complex: true]]
    }
    task.retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    when:
    TaskResult result = task.execute(stage)

    then:
    result.context.val1 == 'one'
    result.context.val2 == [complex: true]

  }

  def "resolves artifact from a property file"() {

    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds",
                                                job: "orca",
                                                buildNumber: 4,
                                                propertyFile: "sample.properties",
                                                expectedArtifacts: [[matchArtifact: [type: "docker/image"]],]])
    def bindTask = new BindProducedArtifactsTask()

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: false]
      getPropertyFile(stage.context.buildNumber, stage.context.propertyFile, stage.context.master, stage.context.job) >>
        [val1: "one", artifacts: [
          [type: "docker/image",
           reference: "gcr.io/project/my-image@sha256:28f82eba",
           name: "gcr.io/project/my-image",
           version: "sha256:28f82eba"],]]
    }
    task.retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }
    bindTask.artifactResolver = artifactResolver
    bindTask.objectMapper = new ObjectMapper()


    when:
    def jenkinsResult = task.execute(stage)
    // We don't have a pipeline, so we pass context manually
    stage.context << jenkinsResult.context
    def bindResult = bindTask.execute(stage)
    def artifacts = bindResult.outputs["artifacts"]

    then:
    bindResult.status == ExecutionStatus.SUCCEEDED
    artifacts.size() == 1
    artifacts[0].name == "gcr.io/project/my-image"

  }

  def "retrieves values from a property file if specified after a failed attempt"() {

    given:
    def stage = new Stage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4, propertyFile: "sample.properties"])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: false]
      getPropertyFile(stage.context.buildNumber, stage.context.propertyFile, stage.context.master, stage.context.job) >>> [[], [val1: "one", val2: "two"]]
    }
    task.retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    when:
    TaskResult result = task.execute(stage)

    then:
    result.context.val1 == 'one'
    result.context.val2 == 'two'

  }

  def "fails stage if property file is expected but not returned from jenkins and build passed"() {
    given:
    def stage = new Stage(pipeline, "jenkins", [master: 'builds', job: 'orca', buildNumber: 4, propertyFile: 'noexist.properties'])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'SUCCESS', running: false]
      getPropertyFile(stage.context.buildNumber, stage.context.propertyFile, stage.context.master, stage.context.job) >> [:]
    }
    task.retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    when:
    task.execute(stage)

    then:
    IllegalStateException e = thrown IllegalStateException
    e.message == 'Expected properties file noexist.properties but it was either missing, empty or contained invalid syntax'
  }

  def "marks 'unstable' results as successful if explicitly configured to do so"() {
    given:
    def stage = new Stage(pipeline, "jenkins",
      [master: "builds", job: "orca", buildNumber: 4, markUnstableAsSuccessful: markUnstableAsSuccessful])


    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.buildNumber, stage.context.master, stage.context.job) >> [result: 'UNSTABLE', building: false]
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    markUnstableAsSuccessful | taskStatus
    true                     | ExecutionStatus.SUCCEEDED
    false                    | ExecutionStatus.TERMINAL
    null                     | ExecutionStatus.TERMINAL
  }
}
