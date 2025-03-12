/*
 * Copyright 2020 Google, LLC
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
 *
 */

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.mock.Calls
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isLessThanOrEqualTo

@ExtendWith(MockKExtension::class)
class PipelineCountsDataProviderTest {

  @MockK
  private lateinit var front50Service: Front50Service

  private lateinit var dataProvider: PipelineCountsDataProvider

  @BeforeEach
  fun setUp() {
    dataProvider = PipelineCountsDataProvider(front50Service)
  }

  @Test
  fun `basic pipeline counts`() {

    every { front50Service.pipelines } returns Calls.response(listOf(
      mapOf(
        "application" to "app1"
      ),
      mapOf(
        "application" to "app2"
      ),
      mapOf(
        "application" to "app2"
      ),
      mapOf(
        "application" to "app2"
      ),
      mapOf(
        "application" to "app3"
      )
    ))

    val result = dataProvider.populateData(
      echoEventForApplication("app2"),
      StatsEvent.getDefaultInstance()
    )

    expectThat(result.spinnakerInstance.pipelineCount).isEqualTo(5)
    expectThat(result.application.pipelineCount).isEqualTo(3)
  }

  @Test
  fun `pipeline without application is ignored`() {

    every { front50Service.pipelines } returns Calls.response(listOf(
      mapOf(
        "application" to "app1"
      ),
      mapOf(
        "application" to "app2"
      ),
      mapOf(
        "application" to "app3"
      ),
      mapOf(
        "noApplicationIsDefined" to "thatsCoolMan"
      )
    ))

    val result = dataProvider.populateData(
      echoEventForApplication("app2"),
      StatsEvent.getDefaultInstance()
    )

    // I don't particularly care if it counts the broken pipeline or not.
    expectThat(result.spinnakerInstance.pipelineCount)
      .isGreaterThanOrEqualTo(3)
      .isLessThanOrEqualTo(4)
    expectThat(result.application.pipelineCount).isEqualTo(1)
  }

  fun echoEventForApplication(application: String): EchoEvent {
    return EchoEvent().apply {
      details = Metadata()
      details.application = application
    }
  }
}
