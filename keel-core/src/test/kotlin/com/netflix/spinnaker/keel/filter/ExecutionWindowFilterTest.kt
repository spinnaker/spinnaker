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
package com.netflix.spinnaker.keel.filter

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.keel.attribute.ExecutionWindow
import com.netflix.spinnaker.keel.attribute.ExecutionWindowAttribute
import com.netflix.spinnaker.keel.attribute.TimeWindow
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId.systemDefault
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ExecutionWindowFilterTest {

  @ParameterizedTest
  @MethodSource("params1")
  fun `intent should be filtered at #scheduledTime with time windows #timeWindows`(
    scheduledTime: Instant,
    expectedResult: Boolean,
    timeWindows: List<TimeWindow>
  ) {
    val subject = ExecutionWindowFilter(
      ExecutionWindowFilter.Configuration(),
      Clock.fixed(scheduledTime, systemDefault())
    )
    val intent = TestIntent(GenericTestIntentSpec("1", emptyMap()), emptyMap(), listOf(
      ExecutionWindowAttribute(ExecutionWindow(emptyList(), timeWindows))
    ))

    subject.filter(intent) shouldMatch equalTo(expectedResult)
  }

  @ParameterizedTest
  @MethodSource("params2")
  fun `should consider whitelisted days when calculating scheduled time`(
    scheduledTime: Instant,
    timeWindows: List<TimeWindow>,
    days: List<Int>,
    expectedResult: Boolean
  ) {
    val subject = ExecutionWindowFilter(
      ExecutionWindowFilter.Configuration(),
      Clock.fixed(scheduledTime, systemDefault())
    )
    val intent = TestIntent(GenericTestIntentSpec("1", emptyMap()), emptyMap(), listOf(
      ExecutionWindowAttribute(ExecutionWindow(days, timeWindows))
    ))

    subject.filter(intent) shouldMatch equalTo(expectedResult)
  }

  @ParameterizedTest
  @MethodSource("params3")
  fun `should be valid all day if no time window selected but some days are selected`(
    scheduledTime: Instant,
    timeWindows: List<TimeWindow>,
    days: List<Int>,
    expectedResult: Boolean
  ) {
    val subject = ExecutionWindowFilter(
      ExecutionWindowFilter.Configuration(),
      Clock.fixed(scheduledTime, systemDefault())
    )
    val intent = TestIntent(GenericTestIntentSpec("1", emptyMap()), emptyMap(), listOf(
      ExecutionWindowAttribute(ExecutionWindow(days, timeWindows))
    ))

    subject.filter(intent) shouldMatch equalTo(expectedResult)
  }

  companion object {
    @JvmStatic
    fun params1(): Iterable<Arguments> = listOf(
      Arguments.of(date("02/14 01:00:00"), true, listOf(window(hourMinute("22:00"), hourMinute("05:00")))),
      Arguments.of(date("02/13 21:45:00"), false, listOf(window(hourMinute("06:00"), hourMinute("10:00")))),
      Arguments.of(date("02/13 13:45:00"), false, listOf(window(hourMinute("22:00"), hourMinute("05:00")))),
      Arguments.of(date("02/13 06:30:00"), false, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 09:59:59"), false, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 10:00:00"), true, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 10:00:35"), true, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 10:01:35"), true, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 09:59:59"), false, listOf(window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 10:00:00"), true, listOf(window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 10:01:35"), true, listOf(window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 10:01:35"), true, listOf(window(hourMinute("16:00"), hourMinute("18:00")), window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 14:30:00"), true, listOf(window(hourMinute("13:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 00:00:00"), false, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 00:01:00"), false, listOf(window(hourMinute("10:00"), hourMinute("13:00")))),
      Arguments.of(date("02/13 00:01:00"), false, listOf(window(hourMinute("15:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 11:01:00"), false, listOf(window(hourMinute("15:00"), hourMinute("18:00")))),
      Arguments.of(date("02/13 14:01:00"), false, listOf(window(hourMinute("13:00"), hourMinute("14:00")))),
      Arguments.of(date("02/13 22:00:00"), true, listOf(window(hourMinute("22:00"), hourMinute("05:00")))),
      Arguments.of(date("02/13 01:00:00"), true, listOf(window(hourMinute("00:00"), hourMinute("05:00")), window(hourMinute("22:00"), hourMinute("23:59")))),
      Arguments.of(date("02/13 00:59:59"), false, listOf(window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")), window(hourMinute("15:00"), hourMinute("16:00")))),
      Arguments.of(date("02/13 10:30:59"), true, listOf(window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")), window(hourMinute("15:00"), hourMinute("16:00")))),
      Arguments.of(date("02/13 12:30:59"), false, listOf(window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")), window(hourMinute("15:00"), hourMinute("16:00")))),
      Arguments.of(date("02/13 16:00:00"), true, listOf(window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")), window(hourMinute("15:00"), hourMinute("16:00")))),
      Arguments.of(date("02/13 16:01:00"), false, listOf(window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")), window(hourMinute("15:00"), hourMinute("16:00"))))
    )

    @JvmStatic
    fun params2() = listOf(
      Arguments.of(date("02/25 01:00:00"), listOf(window(hourMinute("22:00"), hourMinute("05:00"))), listOf(1, 2, 3, 4, 5, 6, 7), true),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), emptyList<Int>(), false),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), listOf(4), false),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), listOf(5), false),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), listOf(3), false),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), listOf(3, 4, 5), false),
      Arguments.of(date("02/25 21:45:00"), listOf(window(hourMinute("06:00"), hourMinute("10:00"))), listOf(3, 5), false)
    )

    @JvmStatic
    fun params3() = listOf(
      Arguments.of(date("02/25 01:00:00"), emptyList<TimeWindow>(), listOf(1, 2, 3, 4, 5, 6, 7), true),
      Arguments.of(date("02/25 00:00:00"), emptyList<TimeWindow>(), listOf(1, 2, 3, 4, 5, 6, 7), true),
      Arguments.of(date("02/25 23:59:00"), emptyList<TimeWindow>(), listOf(1, 2, 3, 4, 5, 6, 7), true),
      Arguments.of(date("02/25 01:00:00"), emptyList<TimeWindow>(), listOf(1), false)
    )

    private fun hourMinute(hourMinuteStr: String): HourMinute =
      hourMinuteStr.split(":").map { it.toInt() }.let { (hour, min) ->
        HourMinute(hour, min)
      }

    private fun date(dateStr: String) =
      ZonedDateTime.parse(
        "$dateStr 2015",
        DateTimeFormatter
          .ofPattern("MM/dd HH:mm:ss yyyy")
          .withZone(systemDefault())
      )
        .toInstant()

    private fun window(start: HourMinute, end: HourMinute): TimeWindow
      = TimeWindow(start.hour, start.min, end.hour, end.min)
  }
}
