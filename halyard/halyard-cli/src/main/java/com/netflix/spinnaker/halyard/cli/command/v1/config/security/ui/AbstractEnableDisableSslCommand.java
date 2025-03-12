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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.ui;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.AbstractEnableDisableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import java.util.function.Supplier;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public abstract class AbstractEnableDisableSslCommand extends AbstractEnableDisableCommand {
  private String targetName = "UI SSL";

  @Override
  public String getShortDescription() {
    return StringUtils.capitalize(getCommandName()) + " SSL for the UI gateway.";
  }

  @Override
  protected Supplier<Void> getOperationSupplier() {
    String deploymentName = getCurrentDeployment();
    return Daemon.setApacheSslEnabled(deploymentName, !noValidate, isEnable());
  }
}
