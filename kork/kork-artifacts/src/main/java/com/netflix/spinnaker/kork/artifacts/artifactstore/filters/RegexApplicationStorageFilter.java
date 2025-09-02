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

import java.util.regex.Pattern;

/**
 * An implementation of ApplicationStorageFilter that uses regular expressions to determine if an
 * application should be filtered from artifact storage operations.
 *
 * <p>This filter matches application names against a configured regular expression pattern. If the
 * application name matches the pattern, it will be filtered (excluded from artifact storage).
 */
public class RegexApplicationStorageFilter implements ApplicationStorageFilter {
  /** The compiled regex pattern used to match application names. */
  private final Pattern filterRegex;

  /**
   * Constructs a RegexApplicationStorageFilter with the specified regex pattern.
   *
   * @param regex The regular expression pattern to match against application names
   */
  public RegexApplicationStorageFilter(String regex) {
    this.filterRegex = Pattern.compile(regex);
  }

  /**
   * Determines if the specified application should be filtered based on regex matching.
   *
   * @param application The name of the application to check
   * @return true if the application name matches the regex pattern (should be filtered), false
   *     otherwise
   */
  @Override
  public boolean filter(String application) {
    return filterRegex.matcher(application).matches();
  }
}
