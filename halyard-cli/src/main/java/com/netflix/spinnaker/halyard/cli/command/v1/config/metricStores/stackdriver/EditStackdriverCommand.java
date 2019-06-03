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

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.stackdriver;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.AbstractEditMetricStoreCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.stackdriver.StackdriverStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;

@Parameters(separators = "=")
public class EditStackdriverCommand extends AbstractEditMetricStoreCommand<StackdriverStore> {
  public MetricStores.MetricStoreType getMetricStoreType() {
    return MetricStores.MetricStoreType.STACKDRIVER;
  }

  @Parameter(
      names = "--credentials-path",
      converter = LocalFileConverter.class,
      description =
          "A path to a Google JSON service account that has permission to publish metrics.")
  private String credentialsPath;

  @Parameter(
      names = "--project",
      description = "The project Spinnaker's metrics should be published to.")
  private String project;

  @Parameter(
      names = "--zone",
      description = "The zone Spinnaker's metrics should be associated with.")
  private String zone;

  @Override
  protected MetricStore editMetricStore(StackdriverStore stackdriverStore) {
    stackdriverStore.setCredentialsPath(
        isSet(credentialsPath) ? credentialsPath : stackdriverStore.getCredentialsPath());
    stackdriverStore.setProject(isSet(project) ? project : stackdriverStore.getProject());
    stackdriverStore.setZone(isSet(zone) ? zone : stackdriverStore.getZone());

    return stackdriverStore;
  }
}
