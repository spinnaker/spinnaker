/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.PipelineConfig
import com.netflix.spinnaker.keel.intent.ApplicationIntent
import com.netflix.spinnaker.keel.tracing.TraceRepository
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import retrofit.RetrofitError
import retrofit.client.Response

object PipelineIntentProcessorTest {

  val traceRepository = mock<TraceRepository>()
  val front50Service = mock<Front50Service>()
  val objectMapper = ObjectMapper()
  val converter = PipelineConverter(objectMapper)

  val subject = PipelineIntentProcessor(traceRepository, front50Service, objectMapper, converter)

  @AfterEach
  fun cleanup() {
    reset(traceRepository, front50Service)
  }

  @Test
  fun `should support PipelineIntent`() {
    subject.supports(ApplicationIntent(mock())) shouldMatch equalTo(false)
    subject.supports(PipelineIntent(PipelineSpec(
      application = "foo",
      name = "bar",
      stages = listOf(),
      triggers = listOf(),
      parameters = listOf(),
      notifications = listOf(),
      flags = PipelineFlags(),
      properties = PipelineProperties()
    ))) shouldMatch equalTo(true)
  }

  @Test
  fun `should save pipeline`() {
    whenever(front50Service.getApplication(any())) doReturn Application(
      name = "foo",
      description = "description",
      email = "example@example.com",
      platformHealthOnly = false,
      platformHealthOnlyShowOverride = false,
      owner = "Example"
    )
    whenever(front50Service.getPipelineConfigs("foo")) doReturn listOf<PipelineConfig>()

    val intent = PipelineIntent(PipelineSpec(
      application = "foo",
      name = "bar",
      stages = listOf(),
      triggers = listOf(),
      parameters = listOf(),
      notifications = listOf(),
      flags = PipelineFlags(),
      properties = PipelineProperties()
    ))

    val result = subject.converge(intent)

    result.orchestrations.size shouldMatch equalTo(1)
    result.orchestrations[0].description shouldMatch equalTo("Create pipeline 'bar'")
    result.orchestrations[0].application shouldMatch equalTo("foo")
    result.orchestrations[0].job[0]["type"] shouldMatch equalTo<Any>("savePipeline")

  }

  @Test
  fun `should skip operation if application is missing`() {
    whenever(front50Service.getApplication(any())) doThrow RetrofitError.httpError(
      "http", Response("", 404, "Not Found", listOf(), mock()), mock(), mock()
    )
    whenever(front50Service.getPipelineConfigs("foo")) doReturn listOf<PipelineConfig>()

    val intent = PipelineIntent(PipelineSpec(
      application = "foo",
      name = "bar",
      stages = listOf(),
      triggers = listOf(),
      parameters = listOf(),
      notifications = listOf(),
      flags = PipelineFlags(),
      properties = PipelineProperties()
    ))

    val result = subject.converge(intent)

    result.orchestrations.size shouldMatch equalTo(0)
    result.changeSummary shouldEqual ChangeSummary("Pipeline:foo:bar", mutableListOf(
      "The application this pipeline is meant for is missing: foo"
    )).apply { type = ChangeType.FAILED_PRECONDITIONS }
  }

}
