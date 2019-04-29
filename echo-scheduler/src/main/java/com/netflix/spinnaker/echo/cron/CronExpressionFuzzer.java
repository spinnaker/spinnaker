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
package com.netflix.spinnaker.echo.cron;

import static java.lang.String.format;

import org.quartz.CronExpression;

/**
 * Rewrites cron expressions that contain a fuzzy expression into an expression that is compatible
 * with Quartz.
 *
 * <p>In the case of a cron meant to run every hour, the normal case would have the trigger fire at
 * the bottom of the hour each time. With fuzzing, the trigger's ID will be hashed and used to
 * jitter the trigger somewhere within that hour.
 *
 * <p>Similar to Jenkins, fuzzy expressions are marked by an "H".
 *
 * <p>Supports hashing of seconds, minutes and hours fields only.
 */
public class CronExpressionFuzzer {

  private static final String TOKEN = "H";

  public static String fuzz(String id, String expression) {
    if (!hasFuzzyExpression(expression)) {
      return expression;
    }
    return Tokens.tokenize(expression).toFuzzedExpression(id);
  }

  public static boolean isValid(String expression) {
    return CronExpression.isValidExpression(fuzz("temp", expression));
  }

  public static boolean hasFuzzyExpression(String expression) {
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

    private Tokens(
        String seconds,
        String minutes,
        String hours,
        String dayOfMonth,
        String month,
        String dayOfWeek,
        String year) {
      this.seconds = seconds;
      this.minutes = minutes;
      this.hours = hours;
      this.dayOfMonth = dayOfMonth;
      this.month = month;
      this.dayOfWeek = dayOfWeek;
      this.year = year;
    }

    static Tokens tokenize(String expression) {
      String[] t = expression.trim().split(" ");
      return new Tokens(t[0], t[1], t[2], t[3], t[4], t[5], (t.length == 7) ? t[6] : null);
    }

    String toFuzzedExpression(String id) {
      if (seconds.contains(TOKEN)) {
        seconds = seconds.replace(TOKEN, hash(id, 59));
      }
      if (minutes.contains(TOKEN)) {
        minutes = minutes.replace(TOKEN, hash(id, 59));
      }
      if (hours.contains(TOKEN)) {
        hours = hours.replace(TOKEN, hash(id, 23));
      }

      return (year == null)
          ? format("%s %s %s %s %s %s", seconds, minutes, hours, dayOfMonth, month, dayOfWeek)
          : format(
              "%s %s %s %s %s %s %s", seconds, minutes, hours, dayOfMonth, month, dayOfWeek, year);
    }

    private String hash(String id, int maxRange) {
      return Integer.toString(Math.abs(id.hashCode() % (maxRange + 1)));
    }
  }
}
