/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.describe
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.intent.*
import com.netflix.spinnaker.keel.tracing.TraceRepository
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import retrofit.RetrofitError.httpError
import retrofit.client.Response

object ApplicationIntentProcessorTest {

  val traceRepository = mock<TraceRepository>()
  val front50Service = mock<Front50Service>()
  val objectMapper = ObjectMapper()

  val subject = ApplicationIntentProcessor(traceRepository, front50Service, objectMapper)

  @AfterEach
  fun cleanup() {
    reset(traceRepository, front50Service)
  }

  @Test
  fun `should support ApplicationIntents`() {
    subject.supports(ParrotIntent(ParrotSpec("hello", "world", 5))) shouldMatch equalTo(false)
    subject.supports(ApplicationIntent(createApplicationSpec())) shouldMatch equalTo(true)
  }

  @Test
  fun `should create application when app is missing`() {
    whenever(front50Service.getApplication("keel")) doThrow httpError("", Response("http://stash.com", 404, "test reason", emptyList(), null), null, null)

    val intent = ApplicationIntent(createApplicationSpec())

    val result = subject.converge(intent)

    result.orchestrations.size shouldMatch equalTo(1)
    result.orchestrations[0].name shouldMatch equalTo("Create application")

    verify(traceRepository).record(any())
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `should update application when app is present`() {
    whenever(front50Service.getApplication("keel")) doReturn
      Application("KEEL", "my original description", "example@example.com", "1", "1", false, false, "example@example.com")

    val updated = createApplicationSpec("my updated description")

    val result = subject.converge(ApplicationIntent(updated))

    result.orchestrations.size shouldMatch equalTo(1)
    result.orchestrations[0].name shouldMatch equalTo("Update application")
    result.orchestrations[0].job[0]["application"] as Map<Any, Any> shouldMatch hasEntry("description", equalTo("my updated description"))

    verify(traceRepository).record(any())
  }

  @Test
  fun `should not update if desired state has not changed`() {
    whenever(front50Service.getApplication("keel")) doReturn
      Application("KEEL", "description", "email@example.com", "1", "1", false, false, "owner").apply {
        details.putAll(mapOf(
          "chaosMonkey" to mapOf(
            "enabled" to true,
            "meanTimeBetweenKillsInWorkDays" to 2,
            "minTimeBetweenKillsInWorkDays" to 2,
            "grouping" to "cluster",
            "regionsAreIndependent" to true,
            "exceptions" to emptyList<String>()
          ),
          "enableRestartRunningExecutions" to false,
          "instanceLinks" to emptyList<String>(),
          "instancePort" to 8087,
          "appGroup" to "Spinnaker",
          "cloudProviders" to "aws",
          "accounts" to "prod,test",
          "dataSources" to mapOf(
            "enabled" to emptyList<String>(),
            "disabled" to emptyList<String>()
          ),
          "requiredGroupMembership" to emptyList<String>(),
          "group" to "spinnaker",
          "providerSettings" to emptyMap<String, Boolean>(),
          "trafficGuards" to emptyList<String>(),
          "notifications" to mapOf(
            "slack" to null,
            "email" to null,
            "sms" to null
          )
        ))
      }

    val desired = createApplicationSpec("description")
    val result = subject.converge(ApplicationIntent(desired))

    result.orchestrations.size shouldMatch equalTo(0)
  }

  fun createApplicationSpec(description: String? = null): ApplicationSpec
    = ApplicationSpec(
    name = "keel",
    description = description ?: "declarative service for spinnaker",
    email = "email@example.com",
    owner = "owner",
    chaosMonkey = ChaosMonkeySpec(
      enabled = true,
      meanTimeBetweenKillsInWorkDays = 2,
      minTimeBetweenKillsInWorkDays = 2,
      grouping = "cluster",
      regionsAreIndependent = true,
      exceptions = emptyList()
    ),
    enableRestartRunningExecutions = false,
    instanceLinks = emptyList(),
    instancePort = 8087,
    appGroup = "Spinnaker",
    cloudProviders = "aws",
    accounts = "prod,test",
    dataSources = DataSourcesSpec(emptyList(), emptyList()),
    requiredGroupMembership = emptyList(),
    group = "spinnaker",
    providerSettings = emptyMap(),
    trafficGuards = emptyList(),
    platformHealthOnlyShowOverride = false,
    platformHealthOnly = false,
    notifications = NotificationSpec(null, null, null)
  )

  private fun hasEntry(key: Any, matcher: Matcher<Any?>): Matcher<Map<Any, Any>> = object : Matcher.Primitive<Map<Any, Any>>() {
    override fun invoke(actual: Map<Any, Any>): MatchResult =
      matcher.invoke(actual[key])

    override val description: String get() = "contains an entry '$key' ${describe(matcher)}"
    override val negatedDescription: String get() = "does not contain an entry '$key' ${describe(matcher)}"
  }

}
