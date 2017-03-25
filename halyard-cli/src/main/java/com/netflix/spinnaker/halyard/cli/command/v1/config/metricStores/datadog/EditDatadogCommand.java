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

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.datadog;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.AbstractEditMetricStoreCommand;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.datadog.DatadogStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;

public class EditDatadogCommand extends AbstractEditMetricStoreCommand<DatadogStore> {
  public MetricStores.MetricStoreType getMetricStoreType() {
    return MetricStores.MetricStoreType.DATADOG;
  }

  @Parameter(
      names = "--api-key",
      description = "Your datadog API key."
  )
  private String apiKey;

  @Parameter(
      names = "--app-key",
      description = "Your datadog app key. This is only required if you want Spinnaker to push pre-configured Spinnaker dashboards to your Datadog account."
  )
  private String appKey;

  @Override
  protected MetricStore editMetricStore(DatadogStore datadogStore) {
    datadogStore.setApiKey(isSet(apiKey) ? apiKey : datadogStore.getApiKey());
    datadogStore.setAppKey(isSet(appKey) ? appKey : datadogStore.getAppKey());

    return datadogStore;
  }
}
