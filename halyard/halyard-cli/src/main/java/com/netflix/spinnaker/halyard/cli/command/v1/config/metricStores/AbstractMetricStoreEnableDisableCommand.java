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
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractMetricStoreEnableDisableCommand extends AbstractMetricStoreCommand {
  @Override
  public String getCommandName() {
    return isEnable() ? "enable" : "disable";
  }

  private String subjunctivePerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  private String indicativePastPerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  protected abstract boolean isEnable();

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Override
  public String getShortDescription() {
    String metricStoreType = getMetricStoreType().getId();
    return "Set the " + metricStoreType + " method as " + subjunctivePerfectAction();
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String metricStoreType = getMetricStoreType().getId();
    boolean enable = isEnable();
    new OperationHandler<Void>()
        .setOperation(
            Daemon.setMetricStoreEnabled(currentDeployment, metricStoreType, !noValidate, enable))
        .setFailureMesssage("Failed to " + getCommandName() + " " + metricStoreType)
        .setSuccessMessage("Successfully " + indicativePastPerfectAction() + " " + metricStoreType)
        .get();
  }
}
