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

import java.time.Duration
import java.util.concurrent.TimeUnit
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import static java.util.Calendar.*

/**
 * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
 * current time is within that window.
 */
@Component
@CompileStatic
@Slf4j
class RestrictExecutionDuringTimeWindow implements StageDefinitionBuilder {

  public static final String TYPE = "restrictExecutionDuringTimeWindow"

  @Override
  def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("suspendExecutionDuringTimeWindow", SuspendExecutionDuringTimeWindowTask)
  }

  @Component
  @VisibleForTesting
  private static class SuspendExecutionDuringTimeWindowTask implements RetryableTask {
    long backoffPeriod = TimeUnit.SECONDS.toMillis(30)
    long timeout = TimeUnit.DAYS.toMillis(7)

    private static final int DAY_START_HOUR = 0
    private static final int DAY_START_MIN = 0
    private static final int DAY_END_HOUR = 23
    private static final int DAY_END_MIN = 59

    @Value('${tasks.executionWindow.timezone:America/Los_Angeles}')
    String timeZoneId

    @Override
    long getDynamicBackoffPeriod(Duration taskDuration) {
      if (taskDuration < Duration.ofMillis(timeout)) {
        // task needs to run again right after it should be complete, so add half a second
        return Duration.ofMillis(timeout).minus(taskDuration).plus(Duration.ofMillis(500)).toMillis()
      } else {
        //start polling normally after timeout to account for delays like throttling
        return backoffPeriod
      }
    }

    @Override
    TaskResult execute(Stage stage) {
      stage.getTopLevelTimeout().ifPresent({ timeout = it })

      Date now = new Date()
      Date scheduledTime
      try {
        scheduledTime = getTimeInWindow(stage, now)
      } catch (Exception e) {
        return new TaskResult(ExecutionStatus.TERMINAL, [failureReason: 'Exception occurred while calculating time window: ' + e.message])
      }
      if (now >= scheduledTime) {
        return new TaskResult(ExecutionStatus.SUCCEEDED)
      } else if (stage.context.skipRemainingWait) {
        return new TaskResult(ExecutionStatus.SUCCEEDED)
      } else {
        stage.scheduledTime = scheduledTime.time
        return new TaskResult(ExecutionStatus.RUNNING)
      }
    }

    @Canonical
    static class RestrictedExecutionWindowConfig {
      ExecutionWindowConfig restrictedExecutionWindow
    }

    @Canonical
    static class ExecutionWindowConfig {
      List<TimeWindowConfig> whitelist
      List<Integer> days = []

      @Override
      String toString() {
        "[ whitelist: ${whitelist}, days: ${days} ]".toString()
      }
    }

    @Canonical
    static class TimeWindowConfig {
      int startHour
      int startMin
      int endHour
      int endMin

      @Override
      String toString() {
        "[ start: ${startHour}:${startMin}, end: ${endHour}:${endMin} ]".toString()
      }
    }

    /**
     * Calculates a time which is within the whitelist of time windows allowed for execution
     * @param stage
     * @param scheduledTime
     * @return
     */
    @VisibleForTesting
    private Date getTimeInWindow(Stage stage, Date scheduledTime) {
      // Passing in the current date to allow unit testing
      try {
        RestrictedExecutionWindowConfig config = stage.mapTo(RestrictedExecutionWindowConfig)
        List whitelistWindows = [] as List<TimeWindow>
        log.info("Calculating scheduled time for ${stage.id}; ${config.restrictedExecutionWindow}")
        for (TimeWindowConfig timeWindow : config.restrictedExecutionWindow.whitelist) {
          HourMinute start = new HourMinute(timeWindow.startHour, timeWindow.startMin)
          HourMinute end = new HourMinute(timeWindow.endHour, timeWindow.endMin)

          whitelistWindows.add(new TimeWindow(start, end))
        }
        return calculateScheduledTime(scheduledTime, whitelistWindows, config.restrictedExecutionWindow.days)

      } catch (IncorrectTimeWindowsException ite) {
        throw new RuntimeException("Incorrect time windows specified", ite)
      }
    }

    @VisibleForTesting
    private Date calculateScheduledTime(Date scheduledTime, List<TimeWindow> whitelistWindows, List<Integer> whitelistDays) throws IncorrectTimeWindowsException {
      return calculateScheduledTime(scheduledTime, whitelistWindows, whitelistDays, false)
    }

    private Date calculateScheduledTime(Date scheduledTime, List<TimeWindow> whitelistWindows, List<Integer> whitelistDays, boolean dayIncremented) throws IncorrectTimeWindowsException {

      if ((!whitelistWindows || whitelistWindows.empty) && whitelistDays && !whitelistDays.empty) {
        whitelistWindows = [ new TimeWindow(new HourMinute(0, 0), new HourMinute(23, 59))]
      }

      boolean inWindow = false
      Collections.sort(whitelistWindows)
      List<TimeWindow> normalized = normalizeTimeWindows(whitelistWindows)
      Calendar calendar = Calendar.instance
      calendar.setTimeZone(TimeZone.getTimeZone(timeZoneId))
      calendar.setTime(scheduledTime)
      boolean todayIsValid = true

      if (whitelistDays && !whitelistDays.empty) {
        int daysIncremented = 0
        while (daysIncremented < 7) {
          boolean nextDayFound = false
          if (whitelistDays.contains(calendar.get(DAY_OF_WEEK))) {
            nextDayFound = true
            todayIsValid = daysIncremented == 0
          }
          if (nextDayFound) {
            break
          }
          calendar.add(DAY_OF_MONTH, 1)
          resetToTomorrow(calendar)
          daysIncremented++
        }
      }
      if (todayIsValid) {
        for (TimeWindow timeWindow : normalized) {
          HourMinute hourMin = new HourMinute(calendar[HOUR_OF_DAY], calendar[MINUTE])
          int index = timeWindow.indexOf(hourMin)
          if (index == -1) {
            calendar[HOUR_OF_DAY] = timeWindow.start.hour
            calendar[MINUTE] = timeWindow.start.min
            calendar[SECOND] = 0
            inWindow = true
            break
          } else if (index == 0) {
            inWindow = true
            break
          }
        }
      }

      if (!inWindow) {
        if (!dayIncremented) {
          resetToTomorrow(calendar)
          return calculateScheduledTime(calendar.time, whitelistWindows, whitelistDays, true)
        } else {
          throw new IncorrectTimeWindowsException("Couldn't calculate a suitable time within given time windows")
        }
      }

      if (dayIncremented) {
        calendar.add(DAY_OF_MONTH, 1)
      }
      return calendar.time
    }

    private static void resetToTomorrow(Calendar calendar) {
      calendar[HOUR_OF_DAY] = DAY_START_HOUR
      calendar[MINUTE] = DAY_START_MIN
      calendar[SECOND] = 0
    }

    private static List<TimeWindow> normalizeTimeWindows(List<TimeWindow> timeWindows) {
      List<TimeWindow> normalized = []
      for (TimeWindow timeWindow : timeWindows) {
        int startHour = timeWindow.start.hour as Integer
        int startMin = timeWindow.start.min as Integer
        int endHour = timeWindow.end.hour as Integer
        int endMin = timeWindow.end.min as Integer

        if (startHour > endHour) {
          HourMinute start1 = new HourMinute(startHour, startMin)
          HourMinute end1 = new HourMinute(DAY_END_HOUR, DAY_END_MIN)
          normalized.add(new TimeWindow(start1, end1))

          HourMinute start2 = new HourMinute(DAY_START_HOUR, DAY_START_MIN)
          HourMinute end2 = new HourMinute(endHour, endMin)
          normalized.add(new TimeWindow(start2, end2))

        } else {
          HourMinute start = new HourMinute(startHour, startMin)
          HourMinute end = new HourMinute(endHour, endMin)
          normalized.add(new TimeWindow(start, end))
        }
      }
      Collections.sort(normalized)
      return normalized
    }

    private static class HourMinute implements Comparable {
      private final int hour
      private final int min

      HourMinute(int hour, int min) {
        this.hour = hour
        this.min = min
      }

      int getHour() {
        return hour
      }

      int getMin() {
        return min
      }

      @Override
      int compareTo(Object o) {
        HourMinute that = (HourMinute) o
        return this.before(that) ? -1 : this.after(that) ? 1 : 0
      }

      boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        HourMinute that = (HourMinute) o
        if (hour != that.hour) return false
        if (min != that.min) return false
        return true
      }

      int hashCode() {
        int result
        result = hour
        result = 31 * result + min
        return result
      }

      boolean before(HourMinute that) {
        return hour < that.hour ? true :
          hour > that.hour ? false :
            min < that.min ? true :
              min > that.min ? false : false
      }

      boolean after(HourMinute that) {
        return hour > that.hour ? true :
          hour < that.hour ? false :
            min > that.min ? true :
              min < that.min ? false : false
      }

      @Override
      String toString() {
        return "${hour}:${min}"
      }
    }

    @VisibleForTesting
    private static class TimeWindow implements Comparable {
      private final HourMinute start
      private final HourMinute end

      TimeWindow(HourMinute start, HourMinute end) {
        this.start = start
        this.end = end
      }

      @Override
      int compareTo(Object o) {
        TimeWindow rhs = (TimeWindow) o
        return this.start.compareTo(rhs.start)
      }

      int indexOf(HourMinute current) {
        if (current.before(this.start)) {
          return -1
        } else if ((current.after(this.start) || current.equals(this.start)) && (current.before(this.end) || current.equals(this.end))) {
          return 0
        } else {
          return 1
        }
      }

      HourMinute getStart() {
        return start
      }

      HourMinute getEnd() {
        return end
      }

      @Override
      String toString() {
        return "{${start} to ${end}}"
      }
    }

    private static class IncorrectTimeWindowsException extends Exception {
      IncorrectTimeWindowsException(String message) {
        super(message)
      }
    }
  }
}
