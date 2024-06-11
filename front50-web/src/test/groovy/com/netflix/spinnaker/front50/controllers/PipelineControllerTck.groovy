/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.Front50SqlProperties
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.ServiceAccountsService
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.hamcrest.Matchers
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.util.UriComponentsBuilder

import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper

import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.*

import static org.hamcrest.Matchers.containsInAnyOrder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


abstract class PipelineControllerTck extends Specification {
  static final int OK = 200
  static final int BAD_REQUEST = 400
  static final int NOT_FOUND = 404
  static final int UNPROCESSABLE_ENTITY = 422

  MockMvc mockMvc

  @Subject
  PipelineDAO pipelineDAO
  ServiceAccountsService serviceAccountsService
  StorageServiceConfigurationProperties.PerObjectType pipelineDAOConfigProperties =
    new StorageServiceConfigurationProperties().getPipeline()

  void setup() {
    println "--------------- Test " + specificationContext.currentIteration.name

    this.pipelineDAO = Spy(createPipelineDAO())
    this.serviceAccountsService = Mock(ServiceAccountsService)

    mockMvc = MockMvcBuilders
      .standaloneSetup(
        new PipelineController(
          pipelineDAO,
          new ObjectMapper(),
          Optional.of(serviceAccountsService),
          Collections.emptyList(),
          Optional.empty()
        )
      )
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()
  }

  abstract PipelineDAO createPipelineDAO()

  def "should fail to save if application is missing"() {
    given:
    def command = [
      name: "some pipeline with no application"
    ]

    when:
    def response = mockMvc
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(command))
      )
      .andReturn()
      .response

    then:
    response.status == UNPROCESSABLE_ENTITY
  }

  void "should provide a valid, unique index when listing all for an application"() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name: "c", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a3", application: "test", index: 3
    ]))

    when:
    def response = mockMvc.perform(get("/pipelines/test"))

    then:
    response
      .andExpect(jsonPath('$.[*].name').value(["a1", "b1", "a3", "b", "c"]))
      .andExpect(jsonPath('$.[*].index').value([0, 1, 2, 3, 4]))
  }

  void 'should update a pipeline'() {
    given:
    def pipeline = pipelineDAO.create(null, new Pipeline([name: "test pipeline", application: "test_application"]))

    when:
    pipeline.name = "Updated Name"
    def response = mockMvc.perform(put("/pipelines/${pipeline.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipeline))).andReturn().response

    then:
    response.status == OK
    pipelineDAO.findById(pipeline.getId()).getName() == "Updated Name"

  }

  void 'should fail update on duplicate pipeline or invalid request'() {
    given:
    def (pipeline1, pipeline2) = [
      pipelineDAO.create(null, new Pipeline([name: "test pipeline 1", application: "test_application"])),
      pipelineDAO.create(null, new Pipeline([name: "test pipeline 2", application: "test_application"]))
    ]

    and:
    pipeline1.name = pipeline2.name

    when:
    def response = mockMvc.perform(put("/pipelines/${pipeline1.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipeline1))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A pipeline with name ${pipeline2.name} already exists in application ${pipeline2.application}"

    when:
    response = mockMvc.perform(put("/pipelines/${pipeline2.id}").contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipeline1))).andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "The provided id ${pipeline1.id} doesn't match the existing pipeline id ${pipeline2.id}"
  }

  @Unroll
  void '(re)generates cron trigger ids for new pipelines, or when explicitly specified: lookupPipelineId: #lookupPipelineId, regenerateCronTriggerIds: #regenerateCronTriggerIds'() {
    given:
    def pipeline = new Pipeline([
      name       : "My Pipeline",
      application: "test",
      triggers   : [
        new Trigger([type: "cron", id: "original-id"])
      ]
    ])
    if (regenerateCronTriggerIds != null) {
      pipeline.setAny("regenerateCronTriggerIds", regenerateCronTriggerIds)
    }
    if (lookupPipelineId) {
      pipelineDAO.create(null, pipeline)
      pipeline.id = pipelineDAO.findById(
        pipelineDAO.getPipelineId("test", "My Pipeline")
      ).getId()
    }

    when:
    def response = mockMvc.perform(post('/pipelines').
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(pipeline)))
      .andReturn().response

    def updatedPipeline = pipelineDAO.findById(
      pipelineDAO.getPipelineId("test", "My Pipeline")
    )

    then:
    response.status == OK
    expectedTriggerCheck.call(updatedPipeline)

    where:
    lookupPipelineId | regenerateCronTriggerIds || expectedTriggerCheck
    false            | null                     || { Pipeline p -> p.triggers*.id != ["original-id"] }
    true             | null                     || { Pipeline p -> p.triggers*.id == ["original-id"] }
    true             | false                    || { Pipeline p -> p.triggers*.id == ["original-id"] }
    true             | true                     || { Pipeline p -> p.triggers*.id != ["original-id"] }
  }

  void 'should ensure that all cron triggers have an identifier'() {
    given:
    def pipeline = new Pipeline([
      name       : "My Pipeline",
      application: "test",
      triggers   : [
        new Trigger([type: "cron", id: "original-id", expression: "1"]),
        new Trigger([type: "cron", expression: "2"]),
        new Trigger([type: "cron", id: "", expression: "3"])
      ]
    ])

    pipelineDAO.create(null, pipeline)
    pipeline.id = pipelineDAO.findById(
      pipelineDAO.getPipelineId("test", "My Pipeline")
    ).getId()

    when:
    def response = mockMvc.perform(post('/pipelines').
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(pipeline)))
      .andReturn().response

    def updatedPipeline = pipelineDAO.findById(
      pipelineDAO.getPipelineId("test", "My Pipeline")
    )

    then:
    response.status == OK
    updatedPipeline.getTriggers().find { it.expression == "1" }.id == "original-id"
    updatedPipeline.getTriggers().find { it.expression == "2" }.id.length() > 1
    updatedPipeline.getTriggers().find { it.expression == "3" }.id.length() > 1
  }

  void 'should delete an existing pipeline by name or id and its associated managed service account'() {
    given:
    def pipelineToDelete = pipelineDAO.create(null, new Pipeline([
      name: "pipeline1", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineDAO.all()
    def allPipelinesForApplication = pipelineDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(delete('/pipelines/test/pipeline1')).andReturn().response

    then:
    response.status == OK
    pipelineDAO.all()*.name == ["pipeline2"]
    1 * serviceAccountsService.deleteManagedServiceAccounts([pipelineToDelete.id])
  }

  void 'should enforce unique names on save operations'() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name: "pipeline1", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "pipeline2", application: "test"
    ]))

    when:
    def allPipelines = pipelineDAO.all()
    def allPipelinesForApplication = pipelineDAO.getPipelinesByApplication("test")

    then:
    allPipelines*.id.sort() == allPipelinesForApplication*.id.sort()
    allPipelines.size() == 2

    when:
    def response = mockMvc.perform(post('/pipelines')
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString([name: "pipeline1", application: "test"])))
      .andReturn().response

    then:
    response.status == BAD_REQUEST
    response.errorMessage == "A pipeline with name pipeline1 already exists in application test"
  }

  @Unroll
  void "pipeline with limitConcurrent = #limitConcurrent and maxConcurrentExecutions = #maxConcurrentExecutions"() {
    def appName = "test"
    def pipelineName = "a_pipeline"
    def Map pipelineData
    given:
    if (type == "max") {
      pipelineData = [
        name: pipelineName,
        application: appName,
        limitConcurrent: limitConcurrent,
        maxConcurrentExecutions: maxConcurrentExecutions
      ]
    } else {
      pipelineData = [
        name: pipelineName,
        application: appName,
        limitConcurrent: limitConcurrent
      ]
    }

    when:
    def postResponse = mockMvc.perform(
      post("/pipelines")
        .contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response
    def postContent = new JsonSlurper().parseText(postResponse.contentAsString)
    def id_from_db = pipelineDAO.getPipelineId(appName, pipelineName)
    def getResponse = mockMvc.perform(
      get("/pipelines/"+id_from_db+"/get"))
      .andReturn()
      .response
    def getContent = new JsonSlurper().parseText(getResponse.contentAsString)

    then:
    postResponse.status == OK
    postContent.id == id_from_db

    getResponse.status == OK
    getContent.limitConcurrent == expectedLimitConcurrent
    getContent.maxConcurrentExecutions == expectedMaxConcurrentExecutions

    where:
      type | limitConcurrent | maxConcurrentExecutions || expectedLimitConcurrent || expectedMaxConcurrentExecutions
      "limit" | false | "not_set" || false || null
      "limit" | true | "not_set" || true || null
      "max" | true | 0 || true || 0
      "max" | true | 5 || true || 5
  }

  @Unroll
  def "multi-threaded cache refresh with synchronizeCacheRefresh: #synchronizeCacheRefresh"() {
    given:
    pipelineDAOConfigProperties.setSynchronizeCacheRefresh(synchronizeCacheRefresh)

    pipelineDAO.create(null, new Pipeline([
      name: "c", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a3", application: "test", index: 3
    ]))

    def results = new ArrayList(10)

    when:
    def futures = new ArrayList(10)
    def threadPool = Executors.newFixedThreadPool(10)
    try {
      10.times {
        futures.add(threadPool.submit({ ->
          mockMvc.perform(get("/pipelines/test"))
        } as Callable))
      }
      futures.each {results.add(it.get())}
    } finally {
      threadPool.shutdown()
    }

    then:
    results.each {
      it.andExpect(jsonPath('$.[*].name').value(["a1", "b1", "a3", "b", "c"]))
        .andExpect(jsonPath('$.[*].index').value([0, 1, 2, 3, 4]))
    }

    where:
    synchronizeCacheRefresh << [ false, true ]
  }

  @Unroll
  def "multi-threaded cache refresh with multiple read/writes and synchronizeCacheRefresh: #synchronizeCacheRefresh"() {
    given:
    pipelineDAOConfigProperties.setSynchronizeCacheRefresh(synchronizeCacheRefresh)

    pipelineDAO.create(null, new Pipeline([
      name: "c", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b", application: "test"
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "b1", application: "test", index: 1
    ]))
    pipelineDAO.create(null, new Pipeline([
      name: "a3", application: "test", index: 3
    ]))

    def results = new ArrayList(10)

    when:
    def futures = new ArrayList(10)
    def threadPool = Executors.newFixedThreadPool(10)
    try {
      10.times {
        futures.add(threadPool.submit({ ->
          if (it % 2 == 0) {
            mockMvc.perform(post('/pipelines')
              .contentType(MediaType.APPLICATION_JSON)
              .content(new ObjectMapper().writeValueAsString([
                name: "My Pipeline" + it,
                application: "test" + it,
                id: "id" + it,
                triggers: []])))
              .andReturn()
              .response
          }

          mockMvc.perform(get("/pipelines/test"))
        } as Callable))
      }
      futures.each {results.add(it.get())}
    } finally {
      threadPool.shutdown()
    }

    then:
    results.each {
      it.andExpect(jsonPath('$.[*].name').value(["a1", "b1", "a3", "b", "c"]))
        .andExpect(jsonPath('$.[*].index').value([0, 1, 2, 3, 4]))
    }

    where:
    synchronizeCacheRefresh << [ false, true ]
  }

  @Unroll
  void "getTriggeredPipelines returns the appropriate pipeline (status: #status)"() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger enabled boolean true",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "valid enabled trigger in disabled pipeline",
      application: "test",
      disabled   : true,
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "triggered by a different pipeline",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "another-triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger with no status",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline" ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger with null status",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline", status: null ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger status not a list",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline", status: "not a list"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger status a list of non-strings",
      application: "test",
      triggers   : [
        [enabled: true, type: "pipeline", pipeline: "triggering-pipeline", status: [ 5 ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger enabled not a boolean",
      application: "test",
      triggers   : [
        [enabled: "foo", type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger enabled string false",
      application: "test",
      triggers   : [
        [enabled: "false", type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger enabled string true",
      application: "test",
      triggers   : [
        [enabled: "true", type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger with no enabled",
      application: "test",
      triggers   : [
        [type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "trigger with null enabled",
      application: "test",
      triggers   : [
        [enabled: null, type: "pipeline", pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "enabled trigger with null type",
      application: "test",
      triggers   : [
        [enabled: true, type: null, pipeline: "triggering-pipeline", status: [ "successful" ] ]
      ]
    ]))

    when:
    def response = mockMvc.perform(get("/pipelines/triggeredBy/triggering-pipeline/${status}/"))

    then:
    response.andReturn().response.status == OK
    if (expectPipelineInResponse) {
      response.andExpect(jsonPath('$.[*].name', Matchers.containsInAnyOrder("trigger enabled boolean true", "trigger enabled string true")))
    } else {
      // Expect an empty list, which is more specific than isEmpty, which might
      // be an empty map({}) or totally empty.
      response.andExpect(content().string("[]"))
    }

    where:
    status       | expectPipelineInResponse
    "failed"     | false
    "successful" | true
  }

  @Unroll
  void "list filters results based on optional query params (enabledPipelines: #enabledPipelines, enabledTriggers: #enabledTriggers, triggerTypes: #triggerTypes)"() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name       : "enabled pipeline with enabled trigger",
      application: "test",
      disabled   : false,
      triggers   : [
        [enabled: true, type: "foo"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "enabled pipeline with disabled trigger",
      application: "test",
      disabled   : false,
      triggers   : [
        [enabled: false, type: "foo"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "implicitly enabled pipeline with enabled trigger",
      application: "test",
      triggers   : [
        [enabled: true, type: "foo"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "disabled pipeline with enabled trigger",
      application: "test",
      disabled   : true,
      triggers   : [
        [enabled: true, type: "foo"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "disabled pipeline with trigger that has no enabled property",
      application: "test",
      disabled   : true,
      triggers   : [
        [type: "foo"]
      ]
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "pipeline with trigger type bar",
      application: "test",
      disabled   : true,
      triggers   : [
        [enabled: true, type: "bar"]
      ]
    ]))

    when:
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
      .fromPath("/pipelines")

    if (enabledPipelines != null) {
      uriComponentsBuilder.queryParam("enabledPipelines", enabledPipelines)
    }

    if (enabledTriggers != null) {
      uriComponentsBuilder.queryParam("enabledTriggers", enabledTriggers)
    }
    if (triggerTypes != null) {
      uriComponentsBuilder.queryParam("triggerTypes", triggerTypes)
    }
    def response = mockMvc.perform(get(uriComponentsBuilder.toUriString()))

    then:
    response.andReturn().response.status == OK
    response.andExpect(jsonPath('$.[*].name', containsInAnyOrder(expectedPipelineNames.toArray(new String[0]))))

    where:
    enabledPipelines | enabledTriggers | triggerTypes   | expectedPipelineNames
    null             | null            | null           | ["disabled pipeline with enabled trigger", "enabled pipeline with enabled trigger", "enabled pipeline with disabled trigger", "disabled pipeline with trigger that has no enabled property", "implicitly enabled pipeline with enabled trigger", "pipeline with trigger type bar"]
    false            | null            | null           | ["disabled pipeline with enabled trigger", "disabled pipeline with trigger that has no enabled property", "pipeline with trigger type bar"]
    true             | null            | null           | ["enabled pipeline with enabled trigger", "enabled pipeline with disabled trigger", "implicitly enabled pipeline with enabled trigger"]
    null             | false           | null           | ["enabled pipeline with disabled trigger", "disabled pipeline with trigger that has no enabled property"]
    null             | true            | null           | ["enabled pipeline with enabled trigger", "implicitly enabled pipeline with enabled trigger", "disabled pipeline with enabled trigger", "pipeline with trigger type bar"]
    null             | null            | "not this one" | []
    null             | null            | "bar"          | ["pipeline with trigger type bar"]
    true             | true            | "foo"          | ["enabled pipeline with enabled trigger", "implicitly enabled pipeline with enabled trigger"]
    false            | true            | "foo,bar"      | ["disabled pipeline with enabled trigger", "pipeline with trigger type bar"]
  }

  void "getByApplicationAndName returns the appropriate pipeline"() {
    given:
    pipelineDAO.create(null, new Pipeline([
      name       : "my-pipeline",
      application: "test",
    ]))
    pipelineDAO.create(null, new Pipeline([
      name       : "my-pipeline",
      application: "another-application",
    ]))

    when:
    def response = mockMvc.perform(get("/pipelines/test/name/my-pipeline/"))

    then:
    1 * pipelineDAO.getPipelineByName("test", "my-pipeline", true)
    response.andReturn().response.status == OK
    response.andExpect(jsonPath('$.name').value("my-pipeline"))
    response.andExpect(jsonPath('$.application').value("test"))

    when:
    response = mockMvc.perform(get("/pipelines/test/name/other-pipeline/"))

    then:
    response.andReturn().response.status == NOT_FOUND
  }

  def "bulkImport of a pipeline with a null id fails"() {
    given:
    def pipelines = [
      new Pipeline([name: "Pipeline1", application: "test", id: "id1"]),
      new Pipeline([name: "Pipeline2", application: "test"])
    ]

    when:
    pipelineDAO.bulkImport(pipelines)

    then:
    thrown NullPointerException

    and:
    // the pipeline with the id didn't make it in either
    pipelineDAO.all(true).size() == 0
  }

  def "should optimally refresh the cache after updates and deletes"() {
    given:
    pipelineDAOConfigProperties.setOptimizeCacheRefreshes(true)
    def pipelines = [
      new Pipeline([name: "Pipeline1", application: "test", id: "id1", triggers: []]),
      new Pipeline([name: "Pipeline2", application: "test", id: "id2", triggers: []]),
      new Pipeline([name: "Pipeline3", application: "test", id: "id3", triggers: []]),
      new Pipeline([name: "Pipeline4", application: "test", id: "id4", triggers: []]),
    ]
    pipelineDAO.bulkImport(pipelines)

    // Test cache refreshes for additions
    when:
    def response = mockMvc.perform(get('/pipelines/test'))

    then:
    response.andReturn().response.status == OK
    response.andExpect(jsonPath('$.[*].name')
      .value(["Pipeline1", "Pipeline2", "Pipeline3", "Pipeline4"]))

    // Test cache refreshes for updates
    when:
    // Update Pipeline 2
    mockMvc.perform(put('/pipelines/id2')
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString(pipelines[1])))
      .andExpect(status().isOk())
    response = mockMvc.perform(get('/pipelines/test'))

    then:
    response.andReturn().response.status == OK
    // ordered of returned pipelines changes after update
    response.andExpect(jsonPath('$.[*].name')
      .value(["Pipeline1", "Pipeline3", "Pipeline4", "Pipeline2"]))

    // Test cache refreshes for deletes
    when:
    // Update 1 pipeline
    mockMvc.perform(delete('/pipelines/test/Pipeline1')).andExpect(status().isOk())
    response = mockMvc.perform(get('/pipelines/test'))

    then:
    response.andReturn().response.status == OK
    response.andExpect(jsonPath('$.[*].name')
      .value(["Pipeline3", "Pipeline4", "Pipeline2"]))
  }

  def "update with a pipeline with a null id throws an exception"() {
    given:
    def pipeline1 = pipelineDAO.create(null, new Pipeline([
      name: "pipeline1", application: "test"
    ]))

    def pipeline2 = pipelineDAO.create(null, new Pipeline([
      name: "pipeline2", application: "test"
    ]))

    when:
    // refresh the in-memory cache and make sure it has two items in it
    def allItems = pipelineDAO.all(true)

    then:
    // verify that the cache has two items to make sure the test is working as expected
    allItems.size() == 2

    when:
    // remove the id from one of the pipelines.
    def pipeline1WithoutId = new Pipeline(name: "pipeline1", application: "test")

    // It's a bug that update allows pipelines without ids.  It's helpful to
    // utilize this bug to uncover others though.
    pipelineDAO.update(pipeline1.id, pipeline1WithoutId);

    then:
    thrown IllegalArgumentException
  }
}

class SqlPipelineControllerTck extends PipelineControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  PipelineDAO createPipelineDAO() {
    def registry = new NoopRegistry()

    def storageService = new SqlStorageService(
      new ObjectMapper(),
      registry,
      currentDatabase.context,
      Clock.systemDefaultZone(),
      new SqlRetryProperties(),
      100,
      "default",
      new Front50SqlProperties()
    )

    pipelineDAOConfigProperties.setRefreshMs(0)
    pipelineDAOConfigProperties.setShouldWarmCache(false)

    pipelineDAO = new DefaultPipelineDAO(storageService,
      scheduler,
      new DefaultObjectKeyLoader(storageService),
      pipelineDAOConfigProperties,
      new NoopRegistry(),
      CircuitBreakerRegistry.ofDefaults())

    return pipelineDAO
  }
}
