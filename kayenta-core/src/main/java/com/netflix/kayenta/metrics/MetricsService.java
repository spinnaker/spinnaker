/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface MetricsService {
  String getType();

  boolean servicesAccount(String accountName);

  default String buildQuery(String metricsAccountName,
                            CanaryConfig canaryConfig,
                            CanaryMetricConfig canaryMetricConfig,
                            CanaryScope canaryScope) throws IOException {
    return "buildQuery() is not implemented for " + this.getClass().getSimpleName() + ".";
  }

  List<MetricSet> queryMetrics(String accountName,
                               CanaryConfig canaryConfig,
                               CanaryMetricConfig canaryMetricConfig,
                               CanaryScope canaryScope) throws IOException;

  default List<Map> getMetadata(String metricsAccountName, String filter) throws IOException {
    return Collections.emptyList();
  }
}
