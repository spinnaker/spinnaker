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
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Permissions.Builder;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Parameters(separators = "=")
public abstract class AbstractEditAccountCommand<T extends Account> extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Deprecated
  @Parameter(
      names = "--add-required-group-membership",
      description = "Add this group to the list of required group memberships."
  )
  private String addRequiredGroupMembership;

  @Deprecated
  @Parameter(
      names = "--remove-required-group-membership",
      description = "Remove this group from the list of required group memberships."
  )
  private String removeRequiredGroupMembership;

  @Deprecated
  @Parameter(
      variableArity = true,
      names = "--required-group-membership",
      description = AccountCommandProperties.REQUIRED_GROUP_MEMBERSHIP_DESCRIPTION
  )
  List<String> requiredGroupMembership;

  @Parameter(
      names = "--add-read-permission",
      description = "Add this permission to the list of read permissions."
  )
  private String addReadPermission;

  @Parameter(
      names = "--remove-read-permission",
      description = "Remove this permission from the list of read permissions."
  )
  private String removeReadPermission;

  @Parameter(
      variableArity = true,
      names = "--read-permissions",
      description = AccountCommandProperties.READ_PERMISSION_DESCRIPTION
  )
  private List<String> readPermissions;

  @Parameter(
      names = "--add-write-permission",
      description = "Add this permission to the list of write permissions."
  )
  private String addWritePermission;

  @Parameter(
      names = "--remove-write-permission",
      description = "Remove this permission to from list of write permissions."
  )
  private String removeWritePermission;

  @Parameter(
      variableArity = true,
      names = "--write-permissions",
      description = AccountCommandProperties.WRITE_PERMISSION_DESCRIPTION
  )
  private List<String> writePermissions;

  @Parameter(
      names = "--provider-version",
      description = AccountCommandProperties.PROVIDER_VERSION_DESCRIPTION
  )
  private Provider.ProviderVersion providerVersion;

  @Parameter(
        names = "--environment",
        arity = 1,
        description = AccountCommandProperties.ENVIRONMENT_DESCRIPTION
  )
  private String environment;

  protected abstract Account editAccount(T account);

  public String getShortDescription() {
    return "Edit an account in the " + getProviderName() + " provider.";
  }

  @Override
  protected List<String> options(String fieldName) {
    String currentDeployment = getCurrentDeployment();
    String accountName = getAccountName("hal-default-account");
    String providerName = getProviderName();

    return new OperationHandler<List<String>>()
        .setFailureMesssage("Failed to get options for field " + fieldName)
        .setOperation(Daemon.getExistingAccountOptions(currentDeployment, providerName, accountName, fieldName))
        .get();
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Account account = new OperationHandler<Account>()
        .setFailureMesssage("Failed to get account " + accountName + " for provider " + providerName + ".")
        .setOperation(Daemon.getAccount(currentDeployment, providerName, accountName, false))
        .get();

    int originaHash = account.hashCode();

    account = editAccount((T) account);

    account.setRequiredGroupMembership(
        updateStringList(account.getRequiredGroupMembership(), requiredGroupMembership, addRequiredGroupMembership, removeRequiredGroupMembership));

    Permissions.Builder permissions = account.getPermissions();

    List<String> resolvedReadPermissions = updateStringList(
        permissions.get(Authorization.READ), readPermissions, addReadPermission, removeReadPermission);
    List<String> resolvedWritePermissions = updateStringList(
        permissions.get(Authorization.WRITE), writePermissions, addWritePermission, removeWritePermission);

    permissions.clear();
    permissions.add(Authorization.READ, resolvedReadPermissions);
    permissions.add(Authorization.WRITE, resolvedWritePermissions);

    account.setProviderVersion(isSet(providerVersion) ? providerVersion : account.getProviderVersion());

    account.setEnvironment(isSet(environment) ? environment : account.getEnvironment());

    if (originaHash == account.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit account " + accountName + " for provider " + providerName + ".")
        .setSuccessMessage("Successfully edited account " + accountName + " for provider " + providerName + ".")
        .setOperation(Daemon.setAccount(currentDeployment, providerName, accountName, !noValidate, account))
        .get();
  }
}
