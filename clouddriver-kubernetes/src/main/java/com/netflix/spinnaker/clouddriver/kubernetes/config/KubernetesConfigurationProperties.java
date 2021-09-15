/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes.config;

import java.util.List;
import lombok.Data;

@Data
public class KubernetesConfigurationProperties {
  private KubernetesJobExecutorProperties jobExecutor = new KubernetesJobExecutorProperties();

  /** flag to toggle account health check. Defaults to true. */
  private boolean verifyAccountHealth = true;

  @Data
  public static class KubernetesJobExecutorProperties {
    private Retries retries = new Retries();

    @Data
    public static class Retries {
      // flag to turn on/off kubectl retry on errors capability.
      private boolean enabled = false;

      // total number of attempts that are made to complete a kubectl call
      int maxAttempts = 3;

      // time in ms to wait before subsequent retry attempts
      long backOffInMs = 5000;

      // list of error strings on which to retry since kubectl binary returns textual error messages
      // back
      List<String> retryableErrorMessages = List.of("TLS handshake timeout");

      // flag to enable exponential backoff - only applicable when enableRetries: true
      boolean exponentialBackoffEnabled = false;

      // only applicable when exponentialBackoff = true
      int exponentialBackoffMultiplier = 2;

      // only applicable when exponentialBackoff = true
      long exponentialBackOffIntervalMs = 10000;
    }
  }
}
