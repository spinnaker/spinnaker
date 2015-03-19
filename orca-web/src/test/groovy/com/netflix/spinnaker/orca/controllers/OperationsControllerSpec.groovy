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

package com.netflix.spinnaker.orca.controllers

import groovy.json.JsonSlurper
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class OperationsControllerSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  def igor = Stub(IgorService)
  def mapper = new OrcaObjectMapper()
  @Subject
    controller = new OperationsController(objectMapper: mapper, pipelineStarter: pipelineStarter, igorService: igor)

  @Unroll
  void '#endpoint accepts #contentType'() {
    given:
    def mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    when:
    def resp = mockMvc.perform(
      post(endpoint).contentType(contentType).content('{}')
    ).andReturn().response

    then:
    1 * pipelineStarter.start(_) >> pipeline

    and:
    resp.status == 200
    slurp(resp.contentAsString).ref == "/pipelines/$pipeline.id"

    where:
    contentType << [MediaType.APPLICATION_JSON, MediaType.valueOf('application/context+json')]
    endpoint = "/orchestrate"
    pipeline = new Pipeline(id: "1")
  }

  private Map slurp(String json) {
    new JsonSlurper().parseText(json)
  }

  def "uses trigger details from pipeline if present"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, null, null, null, null, null)

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo == buildInfo
    }

    where:
    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      trigger: [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber
      ]
    ]
    buildInfo = [result: "SUCCESS"]
  }

  def "uses trigger details from query string if present"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> buildInfo

    when:
    controller.orchestrate([:], user, master, job, buildNumber, null)

    then:
    with(startedPipeline) {
      trigger.type == "manual"
      trigger.user == user
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo == buildInfo
    }

    where:
    user = "fzlem"
    master = "master"
    job = "job"
    buildNumber = 1337
    buildInfo = [result: "SUCCESS"]
  }

  def "query string trigger details override those from the pipeline"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> [result: "SUCCESS"]

    when:
    controller.orchestrate(requestedPipeline, user, master, job, buildNumber, null)

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger.user == user
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
    }

    where:
    requestedPipeline = [
      trigger: [
        type  : "jenkins",
        master: "master",
        job   : "job"
      ]
    ]
    user = "fzlem"
    master = "qs-master"
    job = "qs-job"
    buildNumber = 1337
  }

  def "gets properties file from igor if specified in query string"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> [result: "SUCCESS"]
    igor.getPropertyFile(master, job, buildNumber, propertyFile) >> propertyFileContent

    when:
    controller.orchestrate(requestedPipeline, user, master, job, buildNumber, propertyFile)

    then:
    with(startedPipeline) {
      trigger.propertyFile == propertyFile
      trigger.properties == propertyFileContent
    }

    where:
    requestedPipeline = [
      trigger: [
        type  : "jenkins",
        master: "master",
        job   : "job"
      ]
    ]
    user = "fzlem"
    master = "qs-master"
    job = "qs-job"
    buildNumber = 1337
    propertyFile = "foo.properties"
    propertyFileContent = [foo: "bar"]
  }

  def "gets properties file from igor if specified in pipeline"() {
    given:
    Pipeline startedPipeline = null
    pipelineStarter.start(_) >> { String json ->
      startedPipeline = mapper.readValue(json, Pipeline)
    }
    igor.getBuild(master, job, buildNumber) >> [result: "SUCCESS"]
    igor.getPropertyFile(master, job, buildNumber, propertyFile) >> propertyFileContent

    when:
    controller.orchestrate(requestedPipeline, null, null, null, null, null)

    then:
    with(startedPipeline) {
      trigger.propertyFile == propertyFile
      trigger.properties == propertyFileContent
    }

    where:
    master = "qs-master"
    job = "qs-job"
    buildNumber = 1337
    propertyFile = "foo.properties"
    requestedPipeline = [
      trigger: [
        type        : "jenkins",
        master      : master,
        job         : job,
        buildNumber : buildNumber,
        propertyFile: propertyFile
      ]
    ]
    propertyFileContent = [foo: "bar"]
  }
}
