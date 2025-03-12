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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.deploy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.AbstractRemoteActionCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ConnectDeployCommand extends AbstractRemoteActionCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "connect";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Connect to your Spinnaker deployment.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          "This command connects to your Spinnaker deployment, assuming it was already deployed. In the case of the `Local*` deployment",
          "type, this is a NoOp.");

  @Parameter(
      names = "--service-names",
      description =
          "When supplied, connections to the specified Spinnaker services are opened. "
              + "When omitted, connections to the UI & API servers are opened to allow you to interact with Spinnaker in your browser.",
      variableArity = true)
  List<String> serviceNames = new ArrayList<>();

  @Override
  protected OperationHandler<RemoteAction> getRemoteAction() {
    return new OperationHandler<RemoteAction>()
        .setFailureMesssage("Failed to generate connection script.")
        .setOperation(
            Daemon.connectToDeployment(getCurrentDeployment(), !noValidate, serviceNames));
  }
}
