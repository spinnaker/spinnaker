package com.netflix.spinnaker.keel.lemur

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import java.time.LocalDate
import java.time.ZoneOffset.UTC

class LemurCertificateTests {

  private val certificateJson = javaClass.getResource("/lemur-certificate-response.json")
  private val mapper = configuredObjectMapper()

  @Test
  fun `can parse a lemur certificate payload`() {
    expectCatching {
      mapper.readValue<LemurCertificateResponse>(certificateJson)
    }
      .isSuccess()
      .and {
        with({ items.first() }) {
          get { active }.isFalse()
          get { validityStart } isEqualTo LocalDate.of(2020, 1, 24).atStartOfDay(UTC).toInstant()
          get { validityEnd } isEqualTo LocalDate.of(2021, 1, 24).atTime(12, 0).toInstant(UTC)
          get { name } isEqualTo "fnord.illuminati.org-DigiCertSHA2SecureServerCA-20200124-20210124"
          get { replaces }.isEmpty()
          get { replacedBy }.hasSize(1)

          with({ replacedBy.first() }) {
            get { active }.isTrue()
            get { validityStart } isEqualTo LocalDate.of(2020, 12, 23).atStartOfDay(UTC).toInstant()
            get { validityEnd } isEqualTo LocalDate.of(2021, 12, 24).atTime(23, 59, 59).toInstant(UTC)
            get { name } isEqualTo "fnord.illuminati.org-DigiCertSHA2SecureServerCA-20201223-20211224"
            get { replaces }.isEmpty()
            get { replacedBy }.isEmpty()
          }
        }
      }
  }
}
