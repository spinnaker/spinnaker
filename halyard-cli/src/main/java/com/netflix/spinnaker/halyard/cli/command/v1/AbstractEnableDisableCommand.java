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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import java.util.function.Supplier;

@Parameters(separators = "=")
public abstract class AbstractEnableDisableCommand extends AbstractConfigCommand {
  @Override
  public String getCommandName() {
    return isEnable() ? "enable" : "disable";
  }

  protected String subjunctivePerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  protected String indicativePastPerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  protected abstract boolean isEnable();

  protected abstract String getTargetName();

  protected abstract Supplier<Void> getOperationSupplier();

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setSuccessMessage("Successfully " + indicativePastPerfectAction() + " " + getTargetName())
        .setFailureMesssage("Failed to " + getCommandName() + " " + getTargetName())
        .setOperation(getOperationSupplier())
        .get();
  }
}
