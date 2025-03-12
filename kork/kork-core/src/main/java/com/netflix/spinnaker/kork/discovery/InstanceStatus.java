/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides instance discovery status. */
public enum InstanceStatus {
  UNKNOWN,
  UP, // Ready to receive traffic
  DOWN, // Do not send traffic - healthcheck callback failed
  STARTING, // Do not send traffic - initializations are underway
  OUT_OF_SERVICE; // Do not send traffic - Intentionally shutdown

  private static final Logger log = LoggerFactory.getLogger(InstanceStatus.class);

  public static InstanceStatus from(String s) {
    if (s != null) {
      try {
        return InstanceStatus.valueOf(s.toUpperCase());
      } catch (IllegalArgumentException e) {
        log.debug(
            "Illegal argument supplied to InstanceStatus.valueOf: {}, defaulting to {}",
            s,
            UNKNOWN);
      }
    }
    return UNKNOWN;
  }
}
