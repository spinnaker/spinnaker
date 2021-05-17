package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrcaResponseDeserializationTests : JUnit5Minutests {
  object Fixture {
    val startTime: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    val response = """{
      "id": "test",
      "name": "test",
      "application": "test",
      "buildTime": ${startTime.toEpochMilli()},
      "startTime": ${startTime.toEpochMilli()},
      "endTime": ${startTime.plus(Duration.ofMinutes(10)).toEpochMilli()},
      "status": "SUCCEEDED",
      "execution": {
        "stages": []
      }
    }""".trimIndent()

    val objectMapper = configuredObjectMapper()
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("fields represented as epoch with milliseconds are converted correctly") {
      val deserialized = objectMapper.readValue<ExecutionDetailResponse>(response)
      expect {
        that(deserialized.buildTime).isEqualTo(startTime)
        that(deserialized.startTime).isEqualTo(startTime)
        that(deserialized.endTime).isEqualTo(startTime + Duration.ofMinutes(10))
      }
    }
  }
}