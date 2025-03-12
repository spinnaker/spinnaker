/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NoopCloudMetricProvider implements CloudMetricProvider<CloudMetricDescriptor> {

  @Override
  public String getCloudProvider() {
    return "noop";
  }

  @Override
  public CloudMetricDescriptor getMetricDescriptor(
      String account, String region, Map<String, String> filters) {
    return null;
  }

  @Override
  public List<CloudMetricDescriptor> findMetricDescriptors(
      String account, String region, Map<String, String> filters) {
    return Collections.emptyList();
  }

  @Override
  public CloudMetricStatistics getStatistics(
      String account,
      String region,
      String metricName,
      Map<String, String> filters,
      Long startTime,
      Long endTime) {
    return null;
  }
}
