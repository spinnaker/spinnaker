/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.retrofit.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class RetrofitExceptionHandlerSpec extends Specification {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Subject
  def handler = new RetrofitExceptionHandler()

  @Unroll
  def "should only handle RetrofitError"() {
    expect:
    handler.handles(exception) == supported

    where:
    exception                                           | supported
    RetrofitError.networkError(null, new IOException()) | true
    new RuntimeException()                              | false
    new IllegalArgumentException()                      | false
  }

  def "should handle validation errors (400) encoded within a RetrofitError"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([error: error, errors: errors]))
    def retrofitError = RetrofitError.httpError(
      url, new Response(url, status, reason, [], body), new GsonConverter(new Gson()), Map
    )

    expect:
    with(handler.handle(stepName, retrofitError)) {
      exceptionType == "RetrofitError"
      operation == stepName
      details.url == url
      details.status == status
      details.error == error
      details.errors == errors
      details.responseBody == "{\"error\":\"Error Message\",\"errors\":[\"Error #1\",\"Error #2\"]}"
      details.rootException == null
    }

    where:
    stepName = "Step"
    url = "http://www.google.com"
    status = 400
    reason = "Bad Request"
    error = "Error Message"
    errors = ["Error #1", "Error #2"]
  }

  def "should handle unexpected server errors (5xx) encoded within a RetrofitError"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([
      error: error, exception: rootException, message: message
    ]))
    def retrofitError = RetrofitError.httpError(
      url, new Response(url, status, reason, [], body), new GsonConverter(new Gson()), Map
    )

    expect:
    with(handler.handle(stepName, retrofitError)) {
      exceptionType == "RetrofitError"
      operation == stepName
      details.url == url
      details.status == status
      details.error == error
      details.errors == [message]
      details.rootException == rootException
    }

    where:
    stepName = "Step"
    url = "http://www.google.com"
    status = 500
    reason = "Internal Server Error"
    error = reason
    rootException = "java.lang.RuntimeException"
    message = "Something bad happened"
  }
}
