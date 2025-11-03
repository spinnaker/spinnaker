/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.igor.codebuild

import com.netflix.spinnaker.igor.exceptions.BuildJobError
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.services.codebuild.model.CodeBuildException
import spock.lang.Specification

class AwsCodeBuildRequestInterceptorSpec extends Specification {
  def interceptor = new AwsCodeBuildRequestInterceptor()

  def "should throw BuildJobError in case of a client exception"() {
    when:
    AwsServiceException exception = CodeBuildException.builder()
      .message("err msg")
      .statusCode(400)
      .build()
    def context = Mock(Context.FailedExecution)
    context.exception() >> exception
    interceptor.onExecutionFailure(context, null)

    then:
    BuildJobError err = thrown()
    err.getMessage().contains("err msg")
  }

  def "should throw RuntimeException in case of other exceptions"() {
    when:
    def exception = new IllegalArgumentException("err msg")
    def context = Mock(Context.FailedExecution)
    context.exception() >> exception
    interceptor.onExecutionFailure(context, null)

    then:
    RuntimeException err = thrown()
    err.getMessage().contains("err msg")
  }
}
