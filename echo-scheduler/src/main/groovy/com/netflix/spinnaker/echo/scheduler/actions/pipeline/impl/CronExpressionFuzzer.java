/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl;

import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * Rewrites cron expressions that contain a fuzzy expression into an
 * expression that is compatible with Quartz.
 *
 * In the case of a cron meant to run every hour, the normal case would have
 * the trigger fire at the bottom of the hour each time. With fuzzing, the
 * trigger's ID will be hashed and used to jitter the trigger somewhere
 * within that hour.
 *
 * Similar to Jenkins, fuzzy expressions are marked by an "H".
 *
 * Supports hashing of seconds, minutes and hours fields only.
 */
public class CronExpressionFuzzer {

  private static final String TOKEN = "H";

  @Nonnull
  static String fuzz(@Nonnull String triggerId, @Nonnull String expression) {
    if (!hasFuzzyExpression(expression)) {
      return expression;
    }
    return Tokens.tokenize(expression).toFuzzedExpression(triggerId);
  }

  private static boolean hasFuzzyExpression(@Nonnull String expression) {
    return expression.contains(TOKEN);
  }

  private static class Tokens {
    private String seconds;
    private String minutes;
    private String hours;
    private String dayOfMonth;
    private String month;
    private String dayOfWeek;
    private String year;

    private Tokens(@Nonnull String seconds,
                   @Nonnull String minutes,
                   @Nonnull String hours,
                   @Nonnull String dayOfMonth,
                   @Nonnull String month,
                   @Nonnull String dayOfWeek,
                   String year) {
      this.seconds = seconds;
      this.minutes = minutes;
      this.hours = hours;
      this.dayOfMonth = dayOfMonth;
      this.month = month;
      this.dayOfWeek = dayOfWeek;
      this.year = year;
    }

    static Tokens tokenize(@Nonnull String expression) {
      String[] t = expression.trim().split(" ");
      return new Tokens(t[0], t[1], t[2], t[3], t[4], t[5], (t.length == 7) ? t[6] : null);
    }

    @Nonnull String toFuzzedExpression(@Nonnull String triggerId) {
      if (seconds.contains(TOKEN)) {
        seconds = seconds.replace(TOKEN, hash(triggerId, 59));
      }
      if (minutes.contains(TOKEN)) {
        minutes = minutes.replace(TOKEN, hash(triggerId, 59));
      }
      if (hours.contains(TOKEN)) {
        hours = hours.replace(TOKEN, hash(triggerId, 23));
      }

      return (year == null)
        ? format("%s %s %s %s %s %s", seconds, minutes, hours, dayOfMonth, month, dayOfWeek)
        : format("%s %s %s %s %s %s %s", seconds, minutes, hours, dayOfMonth, month, dayOfWeek, year);
    }

    private @Nonnull String hash(@Nonnull String triggerId, Integer maxRange) {
      return Integer.toString(Math.abs(triggerId.hashCode() % maxRange));
    }
  }
}
