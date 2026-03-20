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

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RegexApplicationStorageFilterTest {
  public static Stream<Arguments> checkFilterArgs() {
    return Stream.of(
        Arguments.of("exact", "exact", true),
        Arguments.of("simple", ".*ple.*", true),
        Arguments.of("negation", "^(?!negation$).*", false),
        Arguments.of("match", "^(?!negation$).*", true));
  }

  @ParameterizedTest
  @MethodSource("checkFilterArgs")
  public void checkFilter(String value, String regex, boolean expectedValue) {
    ApplicationStorageFilter filter = new RegexApplicationStorageFilter(regex);
    assertEquals(expectedValue, filter.filter(value));
  }
}
