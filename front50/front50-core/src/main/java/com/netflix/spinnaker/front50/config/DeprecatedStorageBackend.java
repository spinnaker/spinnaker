/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.front50.config;

import org.slf4j.Logger;

/**
 * Emits a consistent deprecation warning when a non-SQL Front50 metadata storage backend is
 * enabled.
 *
 * <p>Front50 is moving to SQL-only persistence. Object-store and Redis backends remain functional
 * through Spinnaker 2027.0.0 to give operators time to migrate, and are scheduled for removal
 * afterward.
 *
 * @see <a href="https://spinnaker.io/docs/setup/productionize/persistence/front50-sql/">Set up
 *     Front50 to use SQL</a>
 * @see <a href="https://spinnaker.io/docs/releases/roadmap/">Spinnaker roadmap</a>
 */
public final class DeprecatedStorageBackend {

  public static final String REMOVAL_RELEASE = "2027.0.0";
  public static final String MIGRATION_DOCS_URL =
      "https://spinnaker.io/docs/setup/productionize/persistence/front50-sql/";

  private DeprecatedStorageBackend() {}

  /**
   * Builds the standard deprecation warning for {@code backend} (e.g. {@code "S3"}, {@code "GCS"}).
   */
  public static String message(String backend) {
    return "Front50 "
        + backend
        + " metadata storage is deprecated and scheduled for removal after Spinnaker "
        + REMOVAL_RELEASE
        + ". Migrate to SQL ("
        + MIGRATION_DOCS_URL
        + ").";
  }

  /** Logs {@link #message(String)} at WARN on {@code log}. */
  public static void warn(Logger log, String backend) {
    log.warn(message(backend));
  }
}
