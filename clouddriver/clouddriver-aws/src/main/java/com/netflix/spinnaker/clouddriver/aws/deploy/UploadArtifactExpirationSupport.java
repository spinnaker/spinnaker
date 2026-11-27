/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Date;

/** Shared parsing and resolution support for upload artifact expiration values. */
public final class UploadArtifactExpirationSupport {

  private UploadArtifactExpirationSupport() {}

  public static boolean isValid(String expires) {
    if (expires == null || expires.isBlank()) {
      return false;
    }

    try {
      parseRequired(expires);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  public static Date resolve(String expires, Clock clock) {
    if (expires == null || expires.isBlank()) {
      return null;
    }

    TemporalAmount parsedAmount = parseRequired(expires);
    Instant now = clock.instant();
    if (parsedAmount instanceof Duration duration) {
      return Date.from(now.plus(duration));
    }

    if (parsedAmount instanceof Period period) {
      return Date.from(ZonedDateTime.ofInstant(now, clock.getZone()).plus(period).toInstant());
    }

    throw new IllegalArgumentException("Unsupported expires value: " + expires);
  }

  private static TemporalAmount parseRequired(String expires) {
    String normalizedExpires = expires.trim();

    try {
      Duration duration = Duration.parse(normalizedExpires);
      if (duration.isNegative() || duration.isZero()) {
        throw new IllegalArgumentException("Invalid expires value: " + expires);
      }
      return duration;
    } catch (DateTimeException ignored) {
      try {
        Period period = Period.parse(normalizedExpires);
        if (period.isNegative() || period.isZero()) {
          throw new IllegalArgumentException("Invalid expires value: " + expires);
        }
        return period;
      } catch (DateTimeException ignoredPeriod) {
        throw new IllegalArgumentException("Invalid expires value: " + expires, ignoredPeriod);
      }
    }
  }
}
