/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow.SuspendExecutionDuringTimeWindowTask
import static com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow.SuspendExecutionDuringTimeWindowTask.TimeWindow

@Unroll
class RestrictExecutionDuringTimeWindowSpec extends Specification {

  def builder = Spy(new TaskNode.Builder(TaskNode.GraphType.FULL))

  @Subject
  restrictExecutionDuringTimeWindow = new RestrictExecutionDuringTimeWindow()

  void 'stage should be scheduled at #expectedTime when triggered at #scheduledTime with time windows #timeWindows'() {
    when:
    SuspendExecutionDuringTimeWindowTask suspendExecutionDuringTimeWindowTask = new SuspendExecutionDuringTimeWindowTask()
    suspendExecutionDuringTimeWindowTask.timeZoneId = "America/Los_Angeles"
    def result = suspendExecutionDuringTimeWindowTask.calculateScheduledTime(scheduledTime, timeWindows, [])

    then:
    result.equals(expectedTime)

    where:
    scheduledTime          | expectedTime           | timeWindows

    date("02/14 01:00:00") | date("02/14 01:00:00") | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 21:45:00") | date("02/14 06:00:00") | [window(hourMinute("06:00"), hourMinute("10:00"))]

    date("02/13 13:45:00") | date("02/13 22:00:00") | [window(hourMinute("22:00"), hourMinute("05:00"))]
    date("02/13 06:30:00") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 09:59:59") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:00") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:00:35") | date("02/13 10:00:35") | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 10:01:35") | date("02/13 10:01:35") | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 09:59:59") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:00:00") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35") | date("02/13 10:01:35") | [window(hourMinute("10:00"), hourMinute("13:00")), window(hourMinute("16:00"), hourMinute("18:00"))]
    date("02/13 10:01:35") | date("02/13 10:01:35") | [window(hourMinute("16:00"), hourMinute("18:00")), window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 14:30:00") | date("02/13 14:30:00") | [window(hourMinute("13:00"), hourMinute("18:00"))]

    date("02/13 00:00:00") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00"))]
    date("02/13 00:01:00") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("13:00"))]

    date("02/13 00:01:00") | date("02/13 15:00:00") | [window(hourMinute("15:00"), hourMinute("18:00"))]
    date("02/13 11:01:00") | date("02/13 15:00:00") | [window(hourMinute("15:00"), hourMinute("18:00"))]

    date("02/13 14:01:00") | date("02/14 13:00:00") | [window(hourMinute("13:00"), hourMinute("14:00"))]

    date("02/13 22:00:00") | date("02/13 22:00:00") | [window(hourMinute("22:00"), hourMinute("05:00"))]

    date("02/13 01:00:00") | date("02/13 01:00:00") | [window(hourMinute("00:00"), hourMinute("05:00")), window(hourMinute("22:00"), hourMinute("23:59"))]

    date("02/13 00:59:59") | date("02/13 10:00:00") | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                       window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 10:30:59") | date("02/13 10:30:59") | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                       window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 12:30:59") | date("02/13 13:00:00") | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                       window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:00:00") | date("02/13 16:00:00") | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                       window(hourMinute("15:00"), hourMinute("16:00"))]
    date("02/13 16:01:00") | date("02/14 10:00:00") | [window(hourMinute("10:00"), hourMinute("11:00")), window(hourMinute("13:00"), hourMinute("14:00")),
                                                       window(hourMinute("15:00"), hourMinute("16:00"))]
  }

  @Unroll
  void 'stage should consider whitelisted days when calculating scheduled time'() {
    when:
    SuspendExecutionDuringTimeWindowTask suspendExecutionDuringTimeWindowTask = new SuspendExecutionDuringTimeWindowTask()
    suspendExecutionDuringTimeWindowTask.timeZoneId = "America/Los_Angeles"
    def result = suspendExecutionDuringTimeWindowTask.calculateScheduledTime(scheduledTime, timeWindows, days)

    then:
    result.equals(expectedTime)

    where:
    scheduledTime           | timeWindows                                        | days            || expectedTime

    date("02/25 01:00:00")  | [window(hourMinute("22:00"), hourMinute("05:00"))] | [1,2,3,4,5,6,7] || date("02/25 01:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | []              || date("02/26 06:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [4]             || date("02/26 06:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [5]             || date("02/27 06:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3]             || date("03/04 06:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3,4,5]         || date("02/26 06:00:00")
    date("02/25 21:45:00")  | [window(hourMinute("06:00"), hourMinute("10:00"))] | [3,5]           || date("02/27 06:00:00")
  }

  void 'stage should be scheduled at #expectedTime when triggered at #scheduledTime with time windows #stage in stage context'() {
    when:
    SuspendExecutionDuringTimeWindowTask suspendExecutionDuringTimeWindowTask = new SuspendExecutionDuringTimeWindowTask()
    suspendExecutionDuringTimeWindowTask.timeZoneId = "America/Los_Angeles"
    def result = suspendExecutionDuringTimeWindowTask.getTimeInWindow(stage, scheduledTime)

    then:
    result.equals(expectedTime)

    where:
    scheduledTime          | expectedTime           | stage

    date("02/13 06:30:00") | date("02/13 10:00:00") | stage([window("10:00", "13:00")])
    date("02/13 09:59:59") | date("02/13 10:00:00") | stage([window("10:00", "13:00")])
    date("02/13 10:00:00") | date("02/13 10:00:00") | stage([window("10:00", "13:00")])
    date("02/13 10:00:35") | date("02/13 10:00:35") | stage([window("10:00", "13:00")])
    date("02/13 10:01:35") | date("02/13 10:01:35") | stage([window("10:00", "13:00")])

    date("02/13 09:59:59") | date("02/13 10:00:00") | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:00:00") | date("02/13 10:00:00") | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:01:35") | date("02/13 10:01:35") | stage([window("10:00", "13:00"), window("16:00", "18:00")])
    date("02/13 10:01:35") | date("02/13 10:01:35") | stage([window("16:00", "18:00"), window("10:00", "13:00")])

    date("02/13 14:30:00") | date("02/13 14:30:00") | stage([window("13:00", "18:00")])

    date("02/13 00:00:00") | date("02/13 10:00:00") | stage([window("10:00", "13:00")])
    date("02/13 00:01:00") | date("02/13 10:00:00") | stage([window("10:00", "13:00")])

    date("02/13 00:01:00") | date("02/13 15:00:00") | stage([window("15:00", "18:00")])
    date("02/13 11:01:00") | date("02/13 15:00:00") | stage([window("15:00", "18:00")])

    date("02/13 14:01:00") | date("02/14 13:00:00") | stage([window("13:00", "14:00")])

    date("02/13 13:45:00") | date("02/13 22:00:00") | stage([window("22:00", "05:00")])
    date("02/13 22:00:00") | date("02/13 22:00:00") | stage([window("22:00", "05:00")])
    date("02/14 01:00:00") | date("02/14 01:00:00") | stage([window("22:00", "05:00")])

    date("02/13 05:01:00") | date("02/13 22:00:00") | stage([window("22:00", "05:00")])
    date("02/13 01:00:00") | date("02/13 01:00:00") | stage([window("00:00", "05:00"), window("22:00", "23:59")])

    date("02/13 00:59:59") | date("02/13 10:00:00") | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 10:30:59") | date("02/13 10:30:59") | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 12:30:59") | date("02/13 13:00:00") | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])  //*
    date("02/13 16:00:00") | date("02/13 16:00:00") | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")])
    date("02/13 16:01:00") | date("02/14 10:00:00") | stage([window("10:00", "11:00"), window("13:00", "14:00"), window("15:00", "16:00")]) //*
  }

  @Unroll
  void 'should be valid all day if no time window selected but some days are selected'() {
    when:
    SuspendExecutionDuringTimeWindowTask suspendExecutionDuringTimeWindowTask = new SuspendExecutionDuringTimeWindowTask()
    suspendExecutionDuringTimeWindowTask.timeZoneId = "America/Los_Angeles"
    def result = suspendExecutionDuringTimeWindowTask.calculateScheduledTime(scheduledTime, timeWindows, days)

    then:
    result.equals(expectedTime)

    where:
    scheduledTime           | timeWindows                                        | days            || expectedTime

    date("02/25 01:00:00")  | [] | [1,2,3,4,5,6,7] || date("02/25 01:00:00")
    date("02/25 00:00:00")  | [] | [1,2,3,4,5,6,7] || date("02/25 00:00:00")
    date("02/25 23:59:00")  | [] | [1,2,3,4,5,6,7] || date("02/25 23:59:00")
    date("02/25 01:00:00")  | [] | [1]             || date("03/02 00:00:00")
  }

  @Unroll
  void 'stage should optionally configure WaitTask based on restrictedExecutionWindow.jitter'() {
    when:
    def jitterContext = [
      enabled : jitterEnabled,
      maxDelay: max,
      minDelay: min
    ]
    Stage restrictExecutionStage = stage([window("10:00", "13:00")], jitterContext)

    restrictExecutionDuringTimeWindow.taskGraph(restrictExecutionStage, builder)

    then:
    if (jitterEnabled && max != null) {
      assert restrictExecutionStage.context.waitTime != null
      assert (restrictExecutionStage.context.waitTime >= min && restrictExecutionStage.context.waitTime <= max)
    } else {
      assert restrictExecutionStage.context.waitTime == null
    }
    waitTaskCount * builder.withTask("waitForJitter", WaitTask)

    where:
    jitterEnabled | min  | max  | waitTaskCount
    true          | 60   | 600  | 1
    true          | null | 600  | 1
    true          | null | null | 0
    false         | 60   | 600  | 0
    false         | null | null | 0
  }

  private hourMinute(String hourMinuteStr) {
    int hour = hourMinuteStr.tokenize(":").get(0) as Integer
    int min = hourMinuteStr.tokenize(":").get(1) as Integer
    return LocalTime.of(hour, min)
  }

  private Instant date(String dateStr) {
    def sdf = DateTimeFormatter
      .ofPattern("MM/dd HH:mm:ss z yyyy")
      .withZone(ZoneId.of("America/Los_Angeles"))
    return Instant.from(sdf.parse(dateStr + " PST 2015"));
  }

  private TimeWindow window(LocalTime start, LocalTime end) {
    return new TimeWindow(start, end)
  }

  private Map window(String start, String end) {
    int startHour = start.tokenize(":").get(0) as Integer
    int startMin = start.tokenize(":").get(1) as Integer
    int endHour = end.tokenize(":").get(0) as Integer
    int endMin = end.tokenize(":").get(1) as Integer
    return [startHour: startHour, startMin: startMin, endHour: endHour, endMin: endMin]
  }

  private Stage stage(List<Map> windows) {
    Map restrictedExecutionWindow = [whitelist: windows]
    Map context = [restrictedExecutionWindow: restrictedExecutionWindow]
    def pipeline = Execution.newPipeline("orca")
    return new Stage(pipeline, "testRestrictExecution", context)
  }

  private Stage stage(List<Map> windows, Map<String, Object> jitterContext) {
    Map restrictedExecutionWindow = [whitelist: windows]
    restrictedExecutionWindow.jitter = jitterContext
    Map context = [restrictedExecutionWindow: restrictedExecutionWindow]
    def pipeline = Execution.newPipeline("orca")
    return new Stage(pipeline, "testRestrictExecution", context)
  }
}
