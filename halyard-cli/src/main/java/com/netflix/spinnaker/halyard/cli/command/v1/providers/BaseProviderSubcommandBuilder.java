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
import com.netflix.spinnaker.halyard.cli.command.v1.SubcommandBuilder;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class BaseProviderSubcommandBuilder implements SubcommandBuilder {
  @Setter
  AbstractProviderCommand providerCommand;

  public Map<String, NestableCommand> build() {
    Map<String, NestableCommand> subcommands = new HashMap<>();
    String providerName = providerCommand.getProviderName();

    NestableCommand getAccountCommand = new GetAccountCommandBuilder()
        .setProviderName(providerName)
        .build();

    NestableCommand disableCommand = new ProviderEnableDisableCommandBuilder()
        .setProviderName(providerName)
        .setEnable(false)
        .build();

    NestableCommand enableCommand = new ProviderEnableDisableCommandBuilder()
        .setProviderName(providerName)
        .setEnable(true)
        .build();

    subcommands.put(getAccountCommand.getCommandName(), getAccountCommand);
    subcommands.put(disableCommand.getCommandName(), disableCommand);
    subcommands.put(enableCommand.getCommandName(), enableCommand);

    return subcommands;
  }
}
