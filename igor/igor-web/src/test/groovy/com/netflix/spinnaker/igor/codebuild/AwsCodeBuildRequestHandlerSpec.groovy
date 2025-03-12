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

import com.amazonaws.AmazonServiceException
import com.amazonaws.DefaultRequest
import com.amazonaws.Response
import com.amazonaws.services.codebuild.model.InvalidInputException
import com.amazonaws.services.codebuild.model.StartBuildRequest
import com.amazonaws.services.codebuild.model.StartBuildResult
import com.netflix.spinnaker.igor.exceptions.BuildJobError
import spock.lang.Specification

class AwsCodeBuildRequestHandlerSpec extends Specification {
  def handler = new AwsCodeBuildRequestHandler()
  def request = new DefaultRequest(new StartBuildRequest(), "codebuild")
  def response = new Response(new StartBuildResult(), null)

  def "should throw BuildJobError in case of a client exception"() {
    when:
    def exception = new InvalidInputException("err msg")
    exception.setErrorType(AmazonServiceException.ErrorType.Client)
    handler.afterError(request, response, exception)
    then:
    BuildJobError err = thrown()
    err.getMessage().contains("err msg")
  }

  def "should throw RuntimeException in case of other exceptions"() {
    when:
    def exception = new IllegalArgumentException("err msg")
    handler.afterError(request, response, exception)

    then:
    RuntimeException err = thrown()
    err.getMessage().contains("err msg")
  }
}
