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

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Parameters()
public abstract class AbstractEditMetricStoreCommand<T extends MetricStore> extends AbstractMetricStoreCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract MetricStore editMetricStore(T metricStore);

  public String getDescription() {
    return "Edit the " + getMetricStoreType().getId() + " authentication method.";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String metricStoreType = getMetricStoreType().getId();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    MetricStore metricStore = new OperationHandler<MetricStore>()
        .setOperation(Daemon.getMetricStore(currentDeployment, metricStoreType, false))
        .setFailureMesssage("Failed to get " + metricStoreType + " method.")
        .get();

    int originalHash = metricStore.hashCode();

    metricStore = editMetricStore((T) metricStore);

    if (originalHash == metricStore.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setMetricStore(currentDeployment, metricStoreType, !noValidate, metricStore))
        .setFailureMesssage("Failed to edit " + metricStoreType + " method.")
        .setSuccessMessage("Successfully edited " + metricStoreType + " method.")
        .get();
  }
}
