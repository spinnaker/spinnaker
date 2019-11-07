/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.titus

import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusExceptionHandler
import io.grpc.Status
import io.grpc.StatusRuntimeException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TitusExceptionHandlerSpec extends Specification {

  @Subject
  TitusExceptionHandler exceptionHandler = new TitusExceptionHandler()

  def "should passthrough an exception"() {
    given:
    Exception downstream = new RuntimeException("exception")

    when:
    Exception exception = exceptionHandler.handle(downstream)

    then:
    exception == downstream
  }

  @Unroll
  def "Should determine if StatusRuntimeException is retryable based on #status"() {
    given:
    StatusRuntimeException downstream = new StatusRuntimeException(status)

    when:
    TitusException exception = exceptionHandler.handle(downstream) as TitusException

    then:
    exception.retryable == expected

    where:
    status                   | expected
    Status.INVALID_ARGUMENT  | false
    Status.DEADLINE_EXCEEDED | true
  }
}
