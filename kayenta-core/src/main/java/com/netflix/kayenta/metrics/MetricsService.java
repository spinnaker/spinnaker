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

import java.io.IOException;
import java.util.List;

public interface MetricsService {
  boolean servicesAccount(String accountName);

  // These are still placeholder arguments. Each metrics service will have its own set of required/optional arguments. The return type is a placeholder as well.
  List<MetricSet> queryMetrics(String accountName,
                               String metricSetName,
                               String instanceNamePrefix,
                               String intervalStartTime,
                               String intervalEndTime) throws IOException;
}
