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
 *
 * @author sthadeshwar
 */
/**
 * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
 * current time is within that window.
 */
@Component
@CompileStatic
class RestrictExecutionDuringTimeWindow extends LinearStage {
  private static final String MAYO_CONFIG_NAME = "restrictExecutionDuringTimeWindow"

  RestrictExecutionDuringTimeWindow() {
    super(MAYO_CONFIG_NAME)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
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
      TimeZone.'default' = TimeZone.getTimeZone('America/Los_Angeles')
      Date now = getCurrentDate()
      Date scheduledTime
      try {
        scheduledTime = getTimeInWindow(stage, getCurrentDate())
      } catch (Exception e) {
        return new DefaultTaskResult(ExecutionStatus.FAILED, [failureReason: 'Exception occurred while calculating time window: ' + e.message])
      }
      if (now.compareTo(scheduledTime) in [0, 1]) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      } else {
        stage.scheduledTime = scheduledTime.time
        return new DefaultTaskResult(ExecutionStatus.SUSPENDED)
      }
    }

    /**
     * Calculates the date-time which is outside of the blackout window. Also, considers the business hours for window calculation
     * if passed in the stage context.
     * @param stage
     * @param currentDateTime
     * @return
     */
    @VisibleForTesting
    private static Date getTimeInWindow(Stage stage, Date scheduledTime) {  // Passing in the current date to allow unit testing
      try {
        Map restrictedExecutionWindow = stage.context.restrictedExecutionWindow as Map
        List whitelist = restrictedExecutionWindow.whitelist as List<Map>
        List whitelistWindows = [] as List<TimeWindow>

        for (Map timeWindow : whitelist) {
          int startMin = timeWindow.startMin as Integer
          int endMin = timeWindow.endMin as Integer
          int startHour = timeWindow.startHour as Integer
          int endHour = timeWindow.endHour as Integer

          if (startHour > endHour) {
            Date start1 = getUpdatedDate(scheduledTime, startHour, startMin, 0)
            Date end1 = getUpdatedDate(scheduledTime, DAY_END_HOUR, DAY_END_MIN, 0)
            whitelistWindows.add(new TimeWindow(start1, end1))

            Date start2 = getUpdatedDate(scheduledTime, DAY_START_HOUR, DAY_START_MIN, 0)
            Date end2 = getUpdatedDate(scheduledTime, endHour, endMin, 0)
            whitelistWindows.add(new TimeWindow(start2, end2))

          } else {
            Date start = getUpdatedDate(scheduledTime, startHour, startMin, 0)
            Date end = getUpdatedDate(scheduledTime, endHour, endMin, 0)
            whitelistWindows.add(new TimeWindow(start, end))
          }
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
      Collections.sort(whitelistWindows)

      boolean inWindow = false
      for (TimeWindow timeWindow : whitelistWindows) {
        int index = timeWindow.indexOf(scheduledTime)
        if (index == -1) {
          scheduledTime[HOUR_OF_DAY] = timeWindow.start[HOUR_OF_DAY]
          scheduledTime[MINUTE] = timeWindow.start[MINUTE]
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

    private static Date getCurrentDate() {
      Calendar calendar = Calendar.instance
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
      calendar.setTime(new Date())
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
      return calendar.time
    }

    private static Date getUpdatedDate(Date date, int hour, int min, int seconds) {
      Calendar calendar = Calendar.instance
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
      calendar.setTime(date)
      calendar.set(HOUR_OF_DAY, hour)
      calendar.set(MINUTE, min)
      calendar.set(SECOND, seconds)
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
      return calendar.time
    }

    @VisibleForTesting
    private static class TimeWindow implements Comparable {
      private final Date start
      private final Date end

      TimeWindow(Date start, Date end) {
        this.start = start
        this.end = end
      }

      @Override
      int compareTo(Object o) {
        TimeWindow rhs = (TimeWindow) o
        return this.start.compareTo(rhs.start)
      }

      int indexOf(Date current) {
        if (current.before(start)) {
          return -1
        } else  if ((current.equals(start) || current.after(start)) && (current.equals(end) || current.before(end))) {
          return 0
        } else {
          return 1
        }
      }

      Date getStart() {
        return start
      }

      Date getEnd() {
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
