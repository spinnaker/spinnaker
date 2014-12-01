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


package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class CreateApplicationTaskSpec extends Specification {
  @Subject
    task = new CreateApplicationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name": "new application"
    ]
  ]

  def stage = new PipelineStage(new Pipeline(), "CreateApplication", config)

  void "should add full exception details to TaskResult when Front50 returns a status 400"() {
    setup:
    task.front50Service = Mock(Front50Service)
    def retrofitError = Mock(RetrofitError)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * task.front50Service.create(_, _, _) >> { throw retrofitError }
    1 * retrofitError.response >> new Response("", 400, "", [], null)
    1 * retrofitError.getBodyAs(Map) >> [
      errors: ["#1", "#2"]
    ]

    taskResult.outputs.exception == [
      statusCode: 400, operation: "CreateApplication", details: [errors: ["#1", "#2"]]
    ]
  }
}
