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

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.prometheus;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.AbstractEditMetricStoreCommand;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.prometheus.PrometheusStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;

@Parameters(separators = "=")
public class EditPrometheusCommand extends AbstractEditMetricStoreCommand<PrometheusStore> {
  public MetricStores.MetricStoreType getMetricStoreType() {
    return MetricStores.MetricStoreType.PROMETHEUS;
  }

  @Parameter(
      names = "--push-gateway",
      description =
          "The endpoint the monitoring Daemon should push metrics to. If you have configured Prometheus to "
              + "automatically discover all your Spinnaker services and pull metrics from them this is not required.")
  private String pushGateway;

  @Override
  protected MetricStore editMetricStore(PrometheusStore prometheusStore) {
    prometheusStore.setPushGateway(
        isSet(pushGateway) ? pushGateway : prometheusStore.getPushGateway());

    return prometheusStore;
  }
}
