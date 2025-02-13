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

package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

class CronServiceSpec extends Specification {

  void "should return validation message when scheduler service fails with 400 status"() {
    given:
    def msg = "{ \"message\": \"Invalid Cron expression!!!\" }"
    def error = spinnakerHttpException(400, msg)
    def service = new CronService(
        echoService: Mock(EchoService) {
          1 * validateCronExpression("blah") >> { throw error }
        }
    )

    expect:
    service.validateCronExpression("blah") == [ valid: false, message: "Status: 400, Method: GET, URL: http://localhost/, Message: Invalid Cron expression!!!"]
  }

  @Unroll
  void "should propagate Retrofit error when status code is #code"() {
    given:
    def msg = "{ \"message\": \"Invalid Cron expression!!!\" }"
    def error = spinnakerHttpException(code, msg)
    def service = new CronService(
        echoService: Mock(EchoService) {
          1 * validateCronExpression("blah") >> { throw error }
        }
    )

    when:
    service.validateCronExpression("blah")

    then:
    thrown SpinnakerHttpException

    where:
    code << [401, 402, 403, 404, 500]
  }

  void "should return simple isValid map on successful Retrofit response"() {
    def response = [
            message: "The horror! You should reject me because I have some awful message.",
            exception: "This is definitely not valid.",
            error: "No way will you accept this response",
            reality: "You will accept it because the service call did not throw an exception"
        ]
    def service = new CronService(
        echoService: Mock(EchoService) {
          1 * validateCronExpression("totally invalid cron expression") >> Calls.response(response)
        }
    )

    expect:
    service.validateCronExpression("totally invalid cron expression") == [ valid: true, description: null ]
  }

  void "should include description if present when validation is successful"() {
    def response = [
        description: "LGTM"
    ]
    def service = new CronService(
        echoService: Mock(EchoService) {
          1 * validateCronExpression("1 1 1 1 1 1") >> Calls.response(response)
        }
    )

    expect:
    service.validateCronExpression("1 1 1 1 1 1") == [ valid: true, description: 'LGTM' ]
  }

  def spinnakerHttpException(int code, String message) {
    String url = "https://some-url";
    Response<Object> retrofit2Response =
      Response.error(
        code,
        ResponseBody.create(
          MediaType.parse("application/json"), message));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
