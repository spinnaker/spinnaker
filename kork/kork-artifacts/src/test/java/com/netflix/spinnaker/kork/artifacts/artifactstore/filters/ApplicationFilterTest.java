/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.filters;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplicationFilterTest {
  @AfterAll
  public static void cleanup() {
    AuthenticatedRequest.setApplication(null);
  }

  public static Stream<Arguments> filterCheckArgs() {
    return Stream.of(
        Arguments.of(null, "foo", List.of(new RegexApplicationStorageFilter("foo")), true),
        Arguments.of(null, "foo", List.of(new RegexApplicationStorageFilter("nomatch")), false),
        Arguments.of("foo", "foo", List.of(new RegexApplicationStorageFilter("foo")), true),
        Arguments.of("foo", "foo", List.of(new RegexApplicationStorageFilter("nomatch")), false));
  }

  @ParameterizedTest
  @MethodSource("filterCheckArgs")
  public void filterCheck(
      String application,
      String runningApplication,
      List<ApplicationStorageFilter> registeredFilters,
      boolean expectedValue) {
    ApplicationFilter applicationFilter = new ApplicationFilter(registeredFilters);
    boolean shouldFilter;
    if (application == null) {
      shouldFilter = applicationFilter.shouldFilter(runningApplication);
    } else {
      AuthenticatedRequest.setApplication(application);
      shouldFilter = applicationFilter.shouldFilter();
    }
    assertEquals(expectedValue, shouldFilter);
  }
}
