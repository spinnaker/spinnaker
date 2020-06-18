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

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.config.TelemetryConfig.TelemetryConfigProps
import com.netflix.spinnaker.kork.proto.stats.DeploymentMethod
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEqualTo

class SpinnakerInstanceDataProviderTest {

  private lateinit var config: TelemetryConfigProps

  private lateinit var dataProvider: SpinnakerInstanceDataProvider

  @BeforeEach
  fun setUp() {
    config = TelemetryConfigProps()
    dataProvider = SpinnakerInstanceDataProvider(config)
  }

  @Test
  fun `data is copied`() {
    val echoEvent = createLoggableEvent()
    val statsEventInput = StatsEvent.getDefaultInstance()
    config.apply {
      instanceId = "myInstanceId"
      spinnakerVersion = "1.2.3.fun"
      deploymentMethod.type = "halyard"
      deploymentMethod.version = "1.35.0.whee"
    }

    val result = dataProvider.populateData(echoEvent, statsEventInput)

    expectThat(result.application.id).isNotBlank()
    // Make sure it got hashed
    expectThat(result.application.id).isNotEqualTo(echoEvent.details.application)

    expectThat(result.spinnakerInstance.id).isNotBlank()
    // Make sure it got hashed
    expectThat(result.spinnakerInstance.id).isNotEqualTo(config.instanceId)
    expectThat(result.spinnakerInstance.version).isEqualTo(config.spinnakerVersion)
    expectThat(result.spinnakerInstance.deploymentMethod.type).isEqualTo(DeploymentMethod.Type.HALYARD)
    expectThat(result.spinnakerInstance.deploymentMethod.version).isEqualTo(config.deploymentMethod.version)
  }

  @ParameterizedTest
  @EnumSource
  fun `all deployment methods are recognized`(type: DeploymentMethod.Type) {

    assumeTrue(type != DeploymentMethod.Type.UNRECOGNIZED)

    val echoEvent = createLoggableEvent()
    val statsEventInput = StatsEvent.getDefaultInstance()
    config.deploymentMethod.apply {
      this.type = type.toString().toLowerCase()
      version = "version"
    }

    val result = dataProvider.populateData(echoEvent, statsEventInput)

    expectThat(result.spinnakerInstance.deploymentMethod.type).isEqualTo(type)
  }

  @Test
  fun `unknown deployment method`() {

    val echoEvent = createLoggableEvent()
    val statsEventInput = StatsEvent.getDefaultInstance()
    config.deploymentMethod.apply {
      this.type = "some unknown type"
      version = "version"
    }

    val result = dataProvider.populateData(echoEvent, statsEventInput)

    expectThat(result.spinnakerInstance.deploymentMethod.type).isEqualTo(DeploymentMethod.Type.NONE)
  }

  // This choice seems questionable to me, but it's how the code was originally written, so I wrote
  // a test for it
  @Test
  fun `deployment method is NONE is version is unset`() {

    val echoEvent = createLoggableEvent()
    val statsEventInput = StatsEvent.getDefaultInstance()
    config.deploymentMethod.apply {
      this.type = "HALYARD"
    }

    val result = dataProvider.populateData(echoEvent, statsEventInput)

    expectThat(result.spinnakerInstance.deploymentMethod.type).isEqualTo(DeploymentMethod.Type.NONE)
  }

  private fun createLoggableEvent(): EchoEvent {
    return Event().apply {
      details = Metadata().apply {
        type = "orca:orchestration:complete"
        application = "application"
      }
      content = mapOf()
    }
  }
}
