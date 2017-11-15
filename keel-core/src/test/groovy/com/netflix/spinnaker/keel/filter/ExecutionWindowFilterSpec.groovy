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

import com.netflix.spinnaker.keel.attribute.ExecutionWindow
import com.netflix.spinnaker.keel.attribute.ExecutionWindowAttribute
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Unroll
class ExecutionWindowFilterSpec extends Specification {

  def "intent should be filtered at #scheduledTime with time windows #timeWindows"() {
    when:
    def subject = new ExecutionWindowFilter(
      new ExecutionWindowFilter.Configuration(),
      Clock.fixed(Instant.ofEpochMilli(scheduledTime.time), ZoneId.of("America/Los_Angeles"))
    )
    def intent = new TestIntent(new TestIntentSpec("1", [:]), [:], [
      new ExecutionWindowAttribute(new ExecutionWindow([], timeWindows))
    ])

    then:
    expectedResult == subject.filter(intent)

    where:
    scheduledTime          | expectedResult | timeWindows

    date("02/14 01:00:00") | true           | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 21:45:00") | false          | [window(hourMinute("06:00"), hourMinute("10:00"))]

    date("02/13 13:45:00") | false          | [window(hourMinute("22:00"), hourMinute("05:00"))]
    date("02/13 06:30:00") | false          | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 09:59:59") | false          | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:00") | true           | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:35") | true           | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:01:35") | true           | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 09:59:59") | false          | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:00:00") | true           | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35") | true           | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35") | true           | [window(hourMinute("16:00"), hourMinute("18:00")), window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 14:30:00") | true           | [window(hourMinute("13:00"), hourMinute("18:00"))]

    date("02/13 00:00:00") | false          | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 00:01:00") | false          | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 00:01:00") | false          | [window(hourMinute("15:00"), hourMinute("18:00"))]
    date("02/13 11:01:00") | false          | [window(hourMinute("15:00"), hourMinute("18:00"))]

    date("02/13 14:01:00") | false          | [window(hourMinute("13:00"), hourMinute("14:00"))]

    date("02/13 22:00:00") | true           | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 01:00:00") | true           | [window(hourMinute("00:00"), hourMinute("05:00")), window(hourMinute("22:00"), hourMinute("23:59"))]

    date("02/13 00:59:59") | false          | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                               window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 10:30:59") | true           | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                               window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 12:30:59") | false          | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                               window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:00:00") | true           | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                               window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:01:00") | false          | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                               window(hourMinute("15:00"), hourMinute("16:00"))]
  }

  def 'should consider whitelisted days when calculating scheduled time'() {
    when:
    def subject = new ExecutionWindowFilter(
      new ExecutionWindowFilter.Configuration(),
      Clock.fixed(Instant.ofEpochMilli(scheduledTime.time), ZoneId.of("America/Los_Angeles"))
    )
    def intent = new TestIntent(new TestIntentSpec("1", [:]), [:], [
      new ExecutionWindowAttribute(new ExecutionWindow(days, timeWindows))
    ])

    then:
    expectedResult == subject.filter(intent)

    where:
    scheduledTime           | timeWindows                                        | days            || expectedResult

    date("02/25 01:00:00")  | [window(hourMinute("22:00"), hourMinute("05:00"))] | [1,2,3,4,5,6,7] || true
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | []              || false
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [4]             || false
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [5]             || false
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3]             || false
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3,4,5]         || false
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3,5]           || false
  }

  def 'should be valid all day if no time window selected but some days are selected'() {
    when:
    def subject = new ExecutionWindowFilter(
      new ExecutionWindowFilter.Configuration(),
      Clock.fixed(Instant.ofEpochMilli(scheduledTime.time), ZoneId.of("America/Los_Angeles"))
    )
    def intent = new TestIntent(new TestIntentSpec("1", [:]), [:], [
      new ExecutionWindowAttribute(new ExecutionWindow(days, timeWindows))
    ])

    then:
    expectedResult == subject.filter(intent)

    where:
    scheduledTime           | timeWindows | days            || expectedResult

    date("02/25 01:00:00")  | []          | [1,2,3,4,5,6,7] || true
    date("02/25 00:00:00")  | []          | [1,2,3,4,5,6,7] || true
    date("02/25 23:59:00")  | []          | [1,2,3,4,5,6,7] || true
    date("02/25 01:00:00")  | []          | [1]             || false
  }

  private hourMinute(String hourMinuteStr) {
    int hour = hourMinuteStr.tokenize(":").get(0) as Integer
    int min = hourMinuteStr.tokenize(":").get(1) as Integer
    return new HourMinute(hour, min)
  }

  private Date date(String dateStr) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss z yyyy");
    sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
    return sdf.parse(dateStr + " PST 2015")
  }

  private com.netflix.spinnaker.keel.attribute.TimeWindow window(HourMinute start, HourMinute end) {
    return new com.netflix.spinnaker.keel.attribute.TimeWindow(start.hour, start.min, end.hour, end.min)
  }
}
