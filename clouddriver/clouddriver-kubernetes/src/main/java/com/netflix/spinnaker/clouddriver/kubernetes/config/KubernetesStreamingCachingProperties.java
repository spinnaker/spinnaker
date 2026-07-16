/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.config;

import lombok.Data;

@Data
public class KubernetesStreamingCachingProperties {
  private boolean enabled = false;

  private long stopTimeoutMillis = 30_000L;
  private long readinessTimeoutMillis = 30_000L;
  private long livenessTimeoutMillis = 30_000L;

  private int kubeapiDiscoveryConnectionTimeoutMillis = 10_000;
  private int kubeapiDiscoveryReadTimeoutMillis = 30_000;
  private int kubeapiDiscoveryRetryLimit = 3;
  private int kubeapiDiscoveryRetryBackoffMillis = 1_000;
  private boolean kubeapiDiscoveryRetryExponential = false;

  private int eventQueueCapacity = 1000;
  private int bulkedEventQueueCapacity = 100;
  private int bulkMaxEvents = 100;
  private int bulkMaxWaitMillis = 300;

  private int watcherRetryTimeoutMillis = 1_000;
  private int watchTimeoutSeconds = 60 * 5;
}
