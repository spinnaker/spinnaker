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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Parameters()
public abstract class AbstractAddAccountCommand extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  @Parameter(
      variableArity = true,
      names = "--required-group-membership",
      description = AccountCommandProperties.REQUIRED_GROUP_MEMBERSHIP_DESCRIPTION
  )
  List<String> requiredGroupMembership = new ArrayList<>();

  protected abstract Account buildAccount(String accountName);

  protected abstract Account emptyAccount();

  public String getDescription() {
    return "Add an account to the " + getProviderName() + " provider.";
  }

  @Override
  protected List<String> options(String fieldName) {
    String currentDeployment = getCurrentDeployment();
    String accountName = getAccountName("hal-default-account");
    Account account = buildAccount(accountName);
    String providerName = getProviderName();

    return new OperationHandler<List<String>>()
        .setFailureMesssage("Failed to get options for field " + fieldName)
        .setOperation(Daemon.getNewAccountOptions(currentDeployment, providerName, fieldName, account))
        .get();
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    Account account = buildAccount(accountName);
    account.setRequiredGroupMembership(requiredGroupMembership);
    String providerName = getProviderName();

    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to add account " + accountName + " for provider " + providerName + ".")
        .setSuccessMessage("Successfully added account " + accountName + " for provider " + providerName + ".")
        .setOperation(Daemon.addAccount(currentDeployment, providerName, !noValidate, account))
        .get();
  }
}
