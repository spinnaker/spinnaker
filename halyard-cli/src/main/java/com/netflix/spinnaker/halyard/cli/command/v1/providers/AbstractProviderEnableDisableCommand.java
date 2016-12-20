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

package com.netflix.spinnaker.halyard.cli.command.v1.providers;

import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

public abstract class AbstractProviderEnableDisableCommand extends AbstractProviderCommand {
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
  public String getDescription() {
    return "Set the " + getProviderName() + " provider as " + subjunctivePerfectAction();
  }

  private void setEnable() {
    String currentDeployment = Daemon.getCurrentDeployment();
    Daemon.setProviderEnableDisable(currentDeployment, getProviderName(), !noValidate, isEnable());
  }

  @Override
  protected void executeThis() {
    setEnable();
    AnsiUi.success("Successfully " + indicativePastPerfectAction() + " " + getProviderName());
  }
}
