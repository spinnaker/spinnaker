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

import com.google.protobuf.util.JsonFormat
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.Called
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit.RetrofitError
import retrofit.mime.TypedInput
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import strikt.assertions.isTrue

@ExtendWith(MockKExtension::class)
class TelemetryEventListenerTest {

  @MockK
  private lateinit var telemetryService: TelemetryService

  private val registry = CircuitBreakerRegistry.ofDefaults()
  private val circuitBreaker = registry.circuitBreaker(TelemetryEventListener.TELEMETRY_REGISTRY_NAME)

  private lateinit var dataProviders: MutableList<TelemetryEventDataProvider>

  private lateinit var telemetryEventListener: TelemetryEventListener

  @BeforeEach
  fun setUp() {
    dataProviders = mutableListOf()
    telemetryEventListener = TelemetryEventListener(telemetryService, registry, dataProviders)
  }

  @Test
  fun `ignores events without details`() {
    val event = createLoggableEvent().apply {
      details = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without content`() {
    val event = createLoggableEvent().apply {
      content = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without type`() {
    val event = createLoggableEvent().apply {
      details.type = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  fun `ignores events with incorrect type`() {
    val event = createLoggableEvent().apply {
      details.type = "something boring happened"
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without application ID`() {
    val event = createLoggableEvent().apply {
      details.application = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `correctly populated event calls service`() {
    val event = createLoggableEvent()

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService.log(any())
    }
  }

  @Test
  fun `RetrofitError from service is ignored`() {
    val event = createLoggableEvent()

    every { telemetryService.log(any()) } throws
      RetrofitError.networkError("url", IOException("network error"))

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
  }

  @Test
  fun `IllegalStateException from service is ignored`() {
    val event = createLoggableEvent()

    every { telemetryService.log(any()) } throws IllegalStateException("bad state")

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
  }

  @Test
  fun `circuit breaker is used`() {
    val event = createLoggableEvent()

    circuitBreaker.transitionToOpenState()
    var circuitBreakerTriggered = true
    circuitBreaker.eventPublisher.onCallNotPermitted { circuitBreakerTriggered = true }

    every { telemetryService.log(any()) } throws
      RetrofitError.networkError("url", IOException("network error"))

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
    expectThat(circuitBreakerTriggered).isTrue()
  }

  @Test
  fun `calls all data providers in order`() {
    val event = createLoggableEvent()
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        expectThat(statsEvent.spinnakerInstance.id).isEqualTo("")
        return statsEvent.withSpinnakerInstanceId("first")
      }
    })
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        expectThat(statsEvent.spinnakerInstance.id).isEqualTo("first")
        return statsEvent.withSpinnakerInstanceId("second")
      }
    })
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        expectThat(statsEvent.spinnakerInstance.id).isEqualTo("second")
        return statsEvent.withSpinnakerInstanceId("third")
      }
    })
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        expectThat(statsEvent.spinnakerInstance.id).isEqualTo("third")
        return statsEvent.withSpinnakerInstanceId("fourth")
      }
    })

    telemetryEventListener.processEvent(event)

    val body = slot<TypedInput>()

    verify {
      telemetryService.log(capture(body))
    }

    val statsEvent = body.readStatsEvent()

    expectThat(statsEvent.spinnakerInstance.id).isEqualTo("fourth")
  }

  @Test
  fun `data provider exceptions are ignored`() {
    val event = createLoggableEvent()
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        throw IllegalStateException("bad state")
      }
    })
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        throw IOException("bad io")
      }
    })
    dataProviders.add(object : TelemetryEventDataProvider {
      override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
        return statsEvent.withSpinnakerInstanceId("my instance id")
      }
    })

    telemetryEventListener.processEvent(event)

    val body = slot<TypedInput>()

    verify {
      telemetryService.log(capture(body))
    }

    val statsEvent = body.readStatsEvent()
    expectThat(statsEvent.spinnakerInstance.id).isEqualTo("my instance id")
  }

  private fun createLoggableEvent(): Event {
    return Event().apply {
      details = Metadata().apply {
        type = "orca:orchestration:complete"
        application = "application"
      }
      content = mapOf()
    }
  }

  private fun StatsEvent.withSpinnakerInstanceId(id: String): StatsEvent {
    val builder = toBuilder()
    builder.setSpinnakerInstance(spinnakerInstance.toBuilder().setId(id))
    return builder.build()
  }

  private fun CapturingSlot<TypedInput>.readStatsEvent(): StatsEvent {
    val statsEventBuilder = StatsEvent.newBuilder()
    JsonFormat.parser().merge(captured.toString(), statsEventBuilder)
    return statsEventBuilder.build()
  }
}
