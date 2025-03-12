package com.netflix.spinnaker.keel.serialization

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

class ObjectMapperSerializationTests : JUnit5Minutests {

  class ObjectMapperSerializationTestsObject {
     var date = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2024-03-10T12:28:19.228816801Z"))
  }

  fun tests() = rootContext {
    test("Verify instant precision is no more than 6 characters") {
      val mapper = configuredObjectMapper()
      val sampleObject = ObjectMapperSerializationTestsObject()
      val readBack = mapper.readValue(
        mapper.writeValueAsString(sampleObject),
        ObjectMapperSerializationTestsObject::class.java
      )
      // 6 precision is the max allowed via str_to_date in SQL.  See
      // https://dev.mysql.com/doc/refman/8.4/en/date-and-time-type-syntax.html#:~:text=MySQL%20permits%20fractional%20seconds%20for,microseconds%20(6%20digits)%20precision.
      // SO we want to make sure waht we store matches this
      val foramt = DateTimeFormatterBuilder().parseCaseInsensitive().appendInstant(6).parseStrict().toFormatter()
      assertThat(readBack.date).isEqualTo(foramt.format(sampleObject.date))
    }
  }
}
