/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.azure;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureAccount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Parameters(separators = "=")
class AzureAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "azure";
  }

  @Parameter(
      names = "--client-id",
      required = true,
      description = AzureCommandProperties.CLIENT_ID_DESCRIPTION)
  private String clientId;

  @Parameter(
      names = "--app-key",
      password = true,
      required = true,
      description = AzureCommandProperties.APP_KEY_DESCRIPTION)
  private String appKey;

  @Parameter(
      names = "--tenant-id",
      required = true,
      description = AzureCommandProperties.TENANT_ID_DESCRIPTION)
  private String tenantId;

  @Parameter(
      names = "--subscription-id",
      required = true,
      description = AzureCommandProperties.SUBSCRIPTION_ID_DESCRIPTION)
  private String subscriptionId;

  @Parameter(names = "--object-id", description = AzureCommandProperties.OBJECT_ID_DESCRIPTION)
  private String objectId;

  @Parameter(
      names = "--default-resource-group",
      required = true,
      description = AzureCommandProperties.DEFAULT_RESOURCE_GROUP_DESCRIPTION)
  private String defaultResourceGroup;

  @Parameter(
      names = "--default-key-vault",
      required = true,
      description = AzureCommandProperties.DEFAULT_KEY_VAULT_DESCRIPTION)
  private String defaultKeyVault;

  @Parameter(
      names = "--packer-resource-group",
      description = AzureCommandProperties.PACKER_RESOURCE_GROUP_DESCRIPTION)
  private String packerResourceGroup;

  @Parameter(
      names = "--packer-storage-account",
      description = AzureCommandProperties.PACKER_STORAGE_ACCOUNT_DESCRIPTION)
  private String packerStorageAccount;

  @Parameter(
      names = "--regions",
      variableArity = true,
      description = AzureCommandProperties.REGIONS_DESCRIPTION)
  private List<String> regions = new ArrayList<String>(Arrays.asList("westus", "eastus"));

  @Parameter(
      names = "--useSshPublicKey",
      description = AzureCommandProperties.USE_SSH_PUBLIC_KEY_DESCRIPTION)
  private String useSshPublicKey = "true";

  @Override
  protected Account buildAccount(String accountName) {
    return ((AzureAccount) new AzureAccount().setName(accountName))
        .setClientId(clientId)
        .setAppKey(appKey)
        .setTenantId(tenantId)
        .setSubscriptionId(subscriptionId)
        .setObjectId(objectId)
        .setDefaultResourceGroup(defaultResourceGroup)
        .setDefaultKeyVault(defaultKeyVault)
        .setPackerResourceGroup(packerResourceGroup)
        .setPackerStorageAccount(packerStorageAccount)
        .setRegions(regions)
        .setUseSshPublicKey(useSshPublicKey);
  }

  @Override
  protected Account emptyAccount() {
    return new AzureAccount();
  }
}
