/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.kubernetes;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.*;
import java.util.List;

/**
 * Describe a specific kubernetes account
 */
@Parameters()
public class GetKubernetesAccountCommand extends AbstractProviderCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "get-account";

  @Getter(AccessLevel.PROTECTED)
  private String providerName = "kubernetes";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Get a specific Kubernetes account";

  @Parameter(description = "Name of desired account to show", arity = 1)
  List<String> accounts = new ArrayList<>();

  public String getAccountName() {
    switch (accounts.size()) {
      case 0:
        throw new IllegalArgumentException("No account name supplied");
      case 1:
        return accounts.get(0);
      default:
        throw new IllegalArgumentException("More than one account supplied");
    }
  }

  @Override
  protected void executeThis() {
    AnsiUi.success(getAccount(getAccountName()).toString());
  }
}
