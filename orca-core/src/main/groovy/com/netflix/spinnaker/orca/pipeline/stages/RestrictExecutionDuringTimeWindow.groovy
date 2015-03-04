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

package com.netflix.spinnaker.orca.pipeline.stages
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

/**
 * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
 * current time is within that window.
 * @author sthadeshwar
 */
@Component
@CompileStatic
class RestrictExecutionDuringTimeWindow extends LinearStage {
  private static final String MAYO_CONFIG_NAME = "restrictExecutionDuringTimeWindow"

  RestrictExecutionDuringTimeWindow() {
    super(MAYO_CONFIG_NAME)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [buildStep(stage, "suspendExecutionDuringTimeWindow", SuspendExecutionDuringTimeWindowTask)]
  }

  @Component
  @VisibleForTesting
  private static class SuspendExecutionDuringTimeWindowTask implements com.netflix.spinnaker.orca.Task {

    private static final int HOUR_OF_DAY = Calendar.HOUR_OF_DAY
    private static final int MINUTE = Calendar.MINUTE
    private static final int SECOND = Calendar.SECOND

    private static final int DAY_START_HOUR = 0
    private static final int DAY_START_MIN = 0
    private static final int DAY_END_HOUR = 23
    private static final int DAY_END_MIN = 59

    @Override
    TaskResult execute(Stage stage) {
      TimeZone.'default' = TimeZone.getTimeZone("America/Los_Angeles")
      System.setProperty("user.timezone", "America/Los_Angeles")

      Date now = getCurrentDate()
      Date scheduledTime
      try {
        scheduledTime = getTimeInWindow(stage, getCurrentDate())
      } catch (Exception e) {
        return new DefaultTaskResult(ExecutionStatus.FAILED, [failureReason: 'Exception occurred while calculating time window: ' + e.message])
      }
      if (now >= scheduledTime) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      } else {
        stage.scheduledTime = scheduledTime.time
        return new DefaultTaskResult(ExecutionStatus.SUSPENDED)
      }
    }

    /**
     * Calculates a time which is within the whitelist of time windows allowed for execution
     * @param stage
     * @param scheduledTime
     * @return
     */
    @VisibleForTesting
    private static Date getTimeInWindow(Stage stage, Date scheduledTime) {  // Passing in the current date to allow unit testing
      try {
        Map restrictedExecutionWindow = stage.context.restrictedExecutionWindow as Map
        List whitelist = restrictedExecutionWindow.whitelist as List<Map>
        List whitelistWindows = [] as List<TimeWindow>

        for (Map timeWindow : whitelist) {
          HourMinute start = new HourMinute(timeWindow.startHour as Integer, timeWindow.startMin as Integer)
          HourMinute end = new HourMinute(timeWindow.endHour as Integer, timeWindow.endMin as Integer)

          whitelistWindows.add(new TimeWindow(start, end))
        }
        return calculateScheduledTime(scheduledTime, whitelistWindows)

      } catch (IncorrectTimeWindowsException ite) {
        throw new RuntimeException("Incorrect time windows specified", ite)
      }
    }

    @VisibleForTesting
    private static Date calculateScheduledTime(Date scheduledTime, List<TimeWindow> whitelistWindows) throws IncorrectTimeWindowsException {
      return calculateScheduledTime(scheduledTime, whitelistWindows, false)
    }

    private static Date calculateScheduledTime(Date scheduledTime, List<TimeWindow> whitelistWindows, boolean dayIncremented) throws IncorrectTimeWindowsException {

      boolean inWindow = false
      Collections.sort(whitelistWindows)
      List<TimeWindow> normalized = normalizeTimeWindows(whitelistWindows)

      for (TimeWindow timeWindow : normalized) {
        HourMinute hourMin = new HourMinute(scheduledTime[HOUR_OF_DAY], scheduledTime[MINUTE])
        int index = timeWindow.indexOf(hourMin)
        if (index == -1) {
          scheduledTime[HOUR_OF_DAY] = timeWindow.start.hour
          scheduledTime[MINUTE] = timeWindow.start.min
          scheduledTime[SECOND] = 0
          inWindow = true
          break
        } else if (index == 0) {
          inWindow = true
          break
        }
      }

      if (!inWindow) {
        if (!dayIncremented) {
          scheduledTime[HOUR_OF_DAY] = DAY_START_HOUR
          scheduledTime[MINUTE] = DAY_START_MIN
          scheduledTime[SECOND] = 0
          return calculateScheduledTime(scheduledTime, whitelistWindows, true)
        } else {
          throw new IncorrectTimeWindowsException("Couldn't calculate a suitable time within given time windows")
        }
      }

      if (dayIncremented) {
        scheduledTime = scheduledTime + 1
      }
      return scheduledTime
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

    private static Date getCurrentDate() {
      Calendar calendar = Calendar.instance
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
      calendar.setTime(new Date())
      return calendar.time
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
        } else  if ((current.after(this.start) || current.equals(this.start)) && (current.before(this.end) || current.equals(this.end))) {
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
