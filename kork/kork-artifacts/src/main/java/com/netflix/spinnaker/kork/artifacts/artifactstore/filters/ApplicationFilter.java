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

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;

/**
 * Filters artifacts based on application-specific storage filters. This class determines whether an
 * artifact should be filtered based on the application context.
 */
public class ApplicationFilter {
  /** The list of application storage filters to apply. */
  protected final List<ApplicationStorageFilter> filter;

  /**
   * Constructs an ApplicationFilter with the specified filters.
   *
   * @param filter The list of application storage filters to apply
   */
  public ApplicationFilter(List<ApplicationStorageFilter> filter) {
    this.filter = filter;
  }

  /**
   * Determines if filtering should be applied based on the current authenticated application. Uses
   * the application from the current authenticated request context.
   *
   * @return true if the current application should be filtered, false otherwise
   */
  public boolean shouldFilter() {
    return this.shouldFilter(AuthenticatedRequest.getSpinnakerApplication().orElse(null));
  }

  /**
   * Determines if filtering should be applied for the specified application.
   *
   * @param application The application name to check against filters
   * @return true if the application should be filtered, false otherwise. Returns true if
   *     application is null, false if no filters are configured.
   */
  public boolean shouldFilter(String application) {
    if (application == null) {
      return true;
    }

    if (this.filter == null) {
      return false;
    }

    return this.filter.stream().anyMatch(filter -> filter.filter(application));
  }
}
