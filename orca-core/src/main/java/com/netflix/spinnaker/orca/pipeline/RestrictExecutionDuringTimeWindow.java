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

package com.netflix.spinnaker.orca.pipeline;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.orca.ExecutionStatus.*;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Calendar.*;
import static java.util.Collections.singletonList;

/**
 * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
 * current time is within that window.
 */
@Component
public class RestrictExecutionDuringTimeWindow implements StageDefinitionBuilder {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String TYPE = "restrictExecutionDuringTimeWindow";

  @Override
  public void taskGraph(
    @Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("suspendExecutionDuringTimeWindow", SuspendExecutionDuringTimeWindowTask.class);

    try {
      JitterConfig jitter = stage.mapTo("/restrictedExecutionWindow/jitter", JitterConfig.class);
      if (jitter.enabled && jitter.maxDelay > 0) {
        if (jitter.skipManual && stage.getExecution().getTrigger().getType().equals("manual")) {
          return;
        }

        long waitTime = ThreadLocalRandom.current().nextLong(jitter.minDelay, jitter.maxDelay + 1);

        stage.setContext(contextWithWait(stage.getContext(), waitTime));
        builder.withTask("waitForJitter", WaitTask.class);
      }
    } catch (IllegalArgumentException e) {
      // Do nothing
    }
  }

  private Map<String, Object> contextWithWait(Map<String, Object> context, long waitTime) {
    context.putIfAbsent("waitTime", waitTime);
    return context;
  }

  private static class JitterConfig {
    private boolean enabled;
    private boolean skipManual;
    private long minDelay;
    private long maxDelay;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isSkipManual() {
      return skipManual;
    }

    public void setSkipManual(boolean skipManual) {
      this.skipManual = skipManual;
    }

    public long getMinDelay() {
      return minDelay;
    }

    public void setMinDelay(long minDelay) {
      this.minDelay = minDelay;
    }

    public long getMaxDelay() {
      return maxDelay;
    }

    public void setMaxDelay(long maxDelay) {
      this.maxDelay = maxDelay;
    }
  }

  @Component
  @VisibleForTesting
  private static class SuspendExecutionDuringTimeWindowTask implements RetryableTask {
    @Override public long getBackoffPeriod() {
      return TimeUnit.SECONDS.toMillis(30);
    }

    @Override public long getTimeout() {
      return TimeUnit.DAYS.toMillis(7);
    }

    private static final int DAY_START_HOUR = 0;
    private static final int DAY_START_MIN = 0;
    private static final int DAY_END_HOUR = 23;
    private static final int DAY_END_MIN = 59;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${tasks.executionWindow.timezone:America/Los_Angeles}")
    String timeZoneId;

    @Override public @Nonnull TaskResult execute(@Nonnull Stage stage) {
      Instant now = Instant.now();
      Instant scheduledTime;
      try {
        scheduledTime = getTimeInWindow(stage, now);
      } catch (Exception e) {
        return TaskResult.builder(TERMINAL).context(Collections.singletonMap("failureReason", "Exception occurred while calculating time window: " + e.getMessage())).build();
      }
      if (now.equals(scheduledTime) || now.isAfter(scheduledTime)) {
        return TaskResult.SUCCEEDED;
      } else if (parseBoolean(stage.getContext().getOrDefault("skipRemainingWait", "false").toString())) {
        return TaskResult.SUCCEEDED;
      } else {
        stage.setScheduledTime(scheduledTime.toEpochMilli());
        return TaskResult.RUNNING;
      }
    }

    static class RestrictedExecutionWindowConfig {
      private ExecutionWindowConfig restrictedExecutionWindow;

      public ExecutionWindowConfig getRestrictedExecutionWindow() {
        return restrictedExecutionWindow;
      }

      public void setRestrictedExecutionWindow(ExecutionWindowConfig restrictedExecutionWindow) {
        this.restrictedExecutionWindow = restrictedExecutionWindow;
      }
    }

    static class ExecutionWindowConfig {
      private List<TimeWindowConfig> whitelist = new ArrayList<>();
      private List<Integer> days = new ArrayList<>();

      public List<TimeWindowConfig> getWhitelist() {
        return whitelist;
      }

      public void setWhitelist(List<TimeWindowConfig> whitelist) {
        this.whitelist = whitelist;
      }

      public List<Integer> getDays() {
        return days;
      }

      public void setDays(List<Integer> days) {
        this.days = days;
      }

      @Override public String toString() {
        return format("[ whitelist: %s, days: %s ]", whitelist, days);
      }
    }

    static class TimeWindowConfig {
      private int startHour;
      private int startMin;
      private int endHour;
      private int endMin;

      public int getStartHour() {
        return startHour;
      }

      public void setStartHour(int startHour) {
        this.startHour = startHour;
      }

      public int getStartMin() {
        return startMin;
      }

      public void setStartMin(int startMin) {
        this.startMin = startMin;
      }

      public int getEndHour() {
        return endHour;
      }

      public void setEndHour(int endHour) {
        this.endHour = endHour;
      }

      public int getEndMin() {
        return endMin;
      }

      public void setEndMin(int endMin) {
        this.endMin = endMin;
      }

      @Override public String toString() {
        return format("[ start: %d:%s, end: %d:%d ]", startHour, startMin, endHour, endMin);
      }
    }

    /**
     * Calculates a time which is within the whitelist of time windows allowed for execution
     *
     * @param stage
     * @param scheduledTime
     * @return
     */
    @VisibleForTesting
    private Instant getTimeInWindow(Stage stage, Instant scheduledTime) {
      // Passing in the current date to allow unit testing
      try {
        RestrictedExecutionWindowConfig config = stage.mapTo(RestrictedExecutionWindowConfig.class);
        List<TimeWindow> whitelistWindows = new ArrayList<>();
        log.info("Calculating scheduled time for {}; {}", stage.getId(), config.restrictedExecutionWindow);
        for (TimeWindowConfig timeWindow : config.restrictedExecutionWindow.whitelist) {
          LocalTime start = LocalTime.of(timeWindow.startHour, timeWindow.startMin);
          LocalTime end = LocalTime.of(timeWindow.endHour, timeWindow.endMin);

          whitelistWindows.add(new TimeWindow(start, end));
        }
        return calculateScheduledTime(scheduledTime, whitelistWindows, config.restrictedExecutionWindow.days);

      } catch (IncorrectTimeWindowsException ite) {
        throw new RuntimeException("Incorrect time windows specified", ite);
      }
    }

    @VisibleForTesting
    private Instant calculateScheduledTime(Instant scheduledTime, List<TimeWindow> whitelistWindows, List<Integer> whitelistDays) throws IncorrectTimeWindowsException {
      return calculateScheduledTime(scheduledTime, whitelistWindows, whitelistDays, false);
    }

    private Instant calculateScheduledTime(Instant scheduledTime, List<TimeWindow> whitelistWindows, List<Integer> whitelistDays, boolean dayIncremented) throws IncorrectTimeWindowsException {
      if ((whitelistWindows.isEmpty()) && !whitelistDays.isEmpty()) {
        whitelistWindows = singletonList(new TimeWindow(LocalTime.of(0, 0), LocalTime.of(23, 59)));
      }

      boolean inWindow = false;
      Collections.sort(whitelistWindows);
      List<TimeWindow> normalized = normalizeTimeWindows(whitelistWindows);
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeZone(TimeZone.getTimeZone(timeZoneId));
      calendar.setTime(new Date(scheduledTime.toEpochMilli()));
      boolean todayIsValid = true;

      if (!whitelistDays.isEmpty()) {
        int daysIncremented = 0;
        while (daysIncremented < 7) {
          boolean nextDayFound = false;
          if (whitelistDays.contains(calendar.get(DAY_OF_WEEK))) {
            nextDayFound = true;
            todayIsValid = daysIncremented == 0;
          }
          if (nextDayFound) {
            break;
          }
          calendar.add(DAY_OF_MONTH, 1);
          resetToTomorrow(calendar);
          daysIncremented++;
        }
      }
      if (todayIsValid) {
        for (TimeWindow timeWindow : normalized) {
          LocalTime hourMin = LocalTime.of(calendar.get(HOUR_OF_DAY), calendar.get(MINUTE));
          int index = timeWindow.indexOf(hourMin);
          if (index == -1) {
            calendar.set(HOUR_OF_DAY, timeWindow.start.getHour());
            calendar.set(MINUTE, timeWindow.start.getMinute());
            calendar.set(SECOND, 0);
            inWindow = true;
            break;
          } else if (index == 0) {
            inWindow = true;
            break;
          }
        }
      }

      if (!inWindow) {
        if (!dayIncremented) {
          resetToTomorrow(calendar);
          return calculateScheduledTime(calendar.getTime().toInstant(), whitelistWindows, whitelistDays, true);
        } else {
          throw new IncorrectTimeWindowsException("Couldn't calculate a suitable time within given time windows");
        }
      }

      if (dayIncremented) {
        calendar.add(DAY_OF_MONTH, 1);
      }
      return calendar.getTime().toInstant();
    }

    private static void resetToTomorrow(Calendar calendar) {
      calendar.set(HOUR_OF_DAY, DAY_START_HOUR);
      calendar.set(MINUTE, DAY_START_MIN);
      calendar.set(SECOND, 0);
    }

    private static List<TimeWindow> normalizeTimeWindows(List<TimeWindow> timeWindows) {
      List<TimeWindow> normalized = new ArrayList<>();
      for (TimeWindow timeWindow : timeWindows) {
        int startHour = timeWindow.start.getHour();
        int startMin = timeWindow.start.getMinute();
        int endHour = timeWindow.end.getHour();
        int endMin = timeWindow.end.getMinute();

        if (startHour > endHour) {
          LocalTime start1 = LocalTime.of(startHour, startMin);
          LocalTime end1 = LocalTime.of(DAY_END_HOUR, DAY_END_MIN);
          normalized.add(new TimeWindow(start1, end1));

          LocalTime start2 = LocalTime.of(DAY_START_HOUR, DAY_START_MIN);
          LocalTime end2 = LocalTime.of(endHour, endMin);
          normalized.add(new TimeWindow(start2, end2));

        } else {
          LocalTime start = LocalTime.of(startHour, startMin);
          LocalTime end = LocalTime.of(endHour, endMin);
          normalized.add(new TimeWindow(start, end));
        }
      }
      Collections.sort(normalized);
      return normalized;
    }

    @VisibleForTesting
    private static class TimeWindow implements Comparable {
      private final LocalTime start;
      private final LocalTime end;

      TimeWindow(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
      }

      @Override public int compareTo(Object o) {
        TimeWindow rhs = (TimeWindow) o;
        return this.start.compareTo(rhs.start);
      }

      int indexOf(LocalTime current) {
        if (current.isBefore(this.start)) {
          return -1;
        } else if ((current.isAfter(this.start) || current.equals(this.start)) && (current.isBefore(this.end) || current.equals(this.end))) {
          return 0;
        } else {
          return 1;
        }
      }

      LocalTime getStart() {
        return start;
      }

      LocalTime getEnd() {
        return end;
      }

      private static
      final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

      @Override public String toString() {
        return format("{%s to %s}", start.format(FORMAT), end.format(FORMAT));
      }
    }

    private static class IncorrectTimeWindowsException extends Exception {
      IncorrectTimeWindowsException(String message) {
        super(message);
      }
    }
  }
}
