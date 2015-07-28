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
import com.google.gson.Gson
import com.netflix.spinnaker.gate.services.internal.SchedulerService
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Unroll

class CronServiceSpec extends Specification {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  void "should return validation message when scheduler service fails with 400 status"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([message: "Invalid Cron expression!!!"]))
    def error = RetrofitError.httpError("", new Response("", 400, "", [], body), new GsonConverter(new Gson()), Map)
    def service = new CronService(
        schedulerService: Mock(SchedulerService) {
          1 * validateCronExpression("blah") >> { throw error }
        }
    )

    expect:
    service.validateCronExpression("blah") == [ valid: false, message: "Invalid Cron expression!!!"]
  }

  @Unroll
  void "should propagate Retrofit error when status code is #code"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([message: "Invalid Cron expression!!!"]))
    def error = RetrofitError.httpError("", new Response("", code, "", [], body), new GsonConverter(new Gson()), Map)
    def service = new CronService(
        schedulerService: Mock(SchedulerService) {
          1 * validateCronExpression("blah") >> { throw error }
        }
    )

    when:
    service.validateCronExpression("blah")

    then:
    thrown RetrofitError

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
        schedulerService: Mock(SchedulerService) {
          1 * validateCronExpression("totally invalid cron expression") >> response
        }
    )

    expect:
    service.validateCronExpression("totally invalid cron expression") == [ valid: true ]
  }
}
