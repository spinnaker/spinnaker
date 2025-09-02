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

/**
 * Interface for filtering applications in the artifact storage system.
 *
 * <p>Implementations of this interface determine whether a specific application should be filtered
 * from artifact storage operations. This allows for selective artifact storage based on
 * application-specific rules.
 *
 * <p>For example, certain applications might be excluded from artifact storage to reduce storage
 * usage, improve performance, or meet specific compliance requirements.
 */
public interface ApplicationStorageFilter {
  /**
   * Determines if the specified application should be filtered.
   *
   * @param application The name of the application to check
   * @return true if the application should be filtered (excluded from artifact storage), false if
   *     the application should be included in artifact storage
   */
  boolean filter(String application);
}
