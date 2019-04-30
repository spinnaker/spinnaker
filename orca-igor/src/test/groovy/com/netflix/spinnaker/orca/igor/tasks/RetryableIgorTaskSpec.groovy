/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.model.RetryableStageDefinition
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class RetryableIgorTaskSpec extends Specification {
  RetryableStageDefinition jobRequest = Stub(RetryableStageDefinition)
  Stage stage = Mock(Stage)

  @Subject
  RetryableIgorTask task = Spy(RetryableIgorTask) {
    mapStage(stage) >> jobRequest
  }

  def "should delegate to subclass"() {
    given:
    jobRequest.getConsecutiveErrors() >> 0

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> TaskResult.SUCCEEDED
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should return RUNNING status when a retryable exception is thrown"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> { throw stubRetrofitError(500) }
    jobRequest.getConsecutiveErrors() >> 0
    result.status == ExecutionStatus.RUNNING
    result.context.get("consecutiveErrors") == 1
  }

  def "should return RUNNING status when a network error is thrown"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> { throw stubRetrofitNetworkError() }
    jobRequest.getConsecutiveErrors() >> 0
    result.status == ExecutionStatus.RUNNING
    result.context.get("consecutiveErrors") == 1
  }

  def "should propagate the error if a non-retryable exception is thrown"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> { throw stubRetrofitError(404) }
    jobRequest.getConsecutiveErrors() >> 0
    thrown RetrofitError
  }

  def "should propagate the error we have reached the retry limit"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> { throw stubRetrofitError(500) }
    jobRequest.getConsecutiveErrors() >> 5
    thrown RetrofitError
  }

  def "should propagate a non-successful task status"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> TaskResult.ofStatus(ExecutionStatus.TERMINAL)
    jobRequest.getConsecutiveErrors() >> 0
    result.status == ExecutionStatus.TERMINAL
  }

  def "resets the error count on success"() {
    when:
    def result = task.execute(stage)

    then:
    1 * task.tryExecute(jobRequest) >> TaskResult.SUCCEEDED
    jobRequest.getConsecutiveErrors() >> 3
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("consecutiveErrors") == 0
  }

  def stubRetrofitError(int statusCode) {
    return Stub(RetrofitError) {
      getKind() >> RetrofitError.Kind.HTTP
      getResponse() >> new Response("", statusCode, "", Collections.emptyList(), null)
    }
  }

  def stubRetrofitNetworkError() {
    return Stub(RetrofitError) {
      getKind() >> RetrofitError.Kind.NETWORK
    }
  }
}
