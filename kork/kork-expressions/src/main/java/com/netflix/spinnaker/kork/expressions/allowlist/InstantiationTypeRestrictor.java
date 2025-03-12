/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.expressions.allowlist;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class InstantiationTypeRestrictor {
  private Set<Class<?>> allowedTypes =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  String.class,
                  Date.class,
                  Integer.class,
                  Long.class,
                  Double.class,
                  Byte.class,
                  SimpleDateFormat.class,
                  Math.class,
                  Random.class,
                  UUID.class,
                  Boolean.class,
                  LocalDate.class,
                  LocalDateTime.class,
                  DayOfWeek.class,
                  Instant.class,
                  ChronoUnit.class,
                  URLEncoder.class,
                  TemporalAdjusters.class)));

  boolean supports(Class<?> type) {
    return allowedTypes.contains(type);
  }
}
