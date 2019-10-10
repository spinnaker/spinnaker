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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.metricStores.datadog.DatadogStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.newrelic.NewrelicStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.prometheus.PrometheusStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.stackdriver.StackdriverStore;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = false)
@Data
public class MetricStores extends Node {
  private DatadogStore datadog = new DatadogStore();
  private PrometheusStore prometheus = new PrometheusStore();
  private StackdriverStore stackdriver = new StackdriverStore();
  private NewrelicStore newrelic = new NewrelicStore();
  private int period = 30;

  public boolean isEnabled() {
    return datadog.isEnabled()
        || prometheus.isEnabled()
        || stackdriver.isEnabled()
        || newrelic.isEnabled();
  }

  public void setEnabled(boolean ignored) {}

  @Override
  public String getNodeName() {
    return "metricStores";
  }

  public static Class<? extends MetricStore> translateMetricStoreType(String metricStoreType) {
    Optional<? extends Class<?>> res =
        Arrays.stream(MetricStores.class.getDeclaredFields())
            .filter(f -> f.getName().equals(metricStoreType))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends MetricStore>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No metric store with type \"" + metricStoreType + "\" handled by halyard");
    }
  }

  public enum MetricStoreType {
    DATADOG("datadog"),
    PROMETHEUS("prometheus"),
    STACKDRIVER("stackdriver"),
    NEWRELIC("newrelic");

    @Getter private final String id;

    MetricStoreType(String id) {
      this.id = id;
    }
  }
}
