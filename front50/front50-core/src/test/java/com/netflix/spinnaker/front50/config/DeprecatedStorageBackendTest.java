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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class DeprecatedStorageBackendTest {

  @Test
  public void messageNamesBackendAndRemovalTarget() {
    String message = DeprecatedStorageBackend.message("S3");

    assertTrue(message.contains("S3"), message);
    assertTrue(message.contains(DeprecatedStorageBackend.REMOVAL_RELEASE), message);
    assertTrue(message.contains("SQL"), message);
    assertTrue(message.contains(DeprecatedStorageBackend.MIGRATION_DOCS_URL), message);
  }

  @Test
  public void warnLogsStandardMessage() {
    Logger log = mock(Logger.class);

    DeprecatedStorageBackend.warn(log, "GCS");

    verify(log).warn(DeprecatedStorageBackend.message("GCS"));
  }
}
