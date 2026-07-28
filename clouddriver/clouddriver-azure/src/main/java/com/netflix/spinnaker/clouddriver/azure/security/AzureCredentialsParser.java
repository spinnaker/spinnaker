/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.azure.security;

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.moniker.Namer;
import com.netflix.spinnaker.security.authz.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AzureCredentialsParser
    implements CredentialsParser<
        AzureConfigurationProperties.ManagedAccount, AzureNamedAccountCredentials> {

  private static final Logger log = LoggerFactory.getLogger(AzureCredentialsParser.class);

  private final NamerRegistry namerRegistry;

  public AzureCredentialsParser(NamerRegistry namerRegistry) {
    this.namerRegistry = namerRegistry;
  }

  @Override
  public AzureNamedAccountCredentials parse(
      AzureConfigurationProperties.ManagedAccount managedAccount) {
    String namingStrategy = managedAccount.getNamingStrategy();
    if (namingStrategy == null) {
      namingStrategy = "default";
    }

    try {
      if (namerRegistry != null) {
        Namer namer = namerRegistry.getNamingStrategy(namingStrategy);
        if (namer != null) {
          NamerRegistry.lookup()
              .withProvider("azure")
              .withAccount(managedAccount.getName())
              .setNamer(Object.class, namer);
        }
      }
    } catch (Exception e) {
      log.warn(
          "Error registering naming strategy for account {}: {}",
          managedAccount.getName(),
          e.getMessage());
    }

    // managedAccount.getPermissions() returns a Permissions.Builder; build it into an immutable
    // Permissions (Permissions.EMPTY when no roles are configured).
    Permissions permissions = null;
    try {
      Permissions.Builder permissionsBuilder = managedAccount.getPermissions();
      if (permissionsBuilder != null && !permissionsBuilder.isEmpty()) {
        permissions = permissionsBuilder.build();
      }
    } catch (Exception e) {
      log.warn(
          "Error building permissions for account {}: {}",
          managedAccount.getName(),
          e.getMessage());
    }

    return new AzureNamedAccountCredentials(
        managedAccount.getName(),
        managedAccount.getEnvironment() != null
            ? managedAccount.getEnvironment()
            : managedAccount.getName(),
        managedAccount.getAccountType() != null
            ? managedAccount.getAccountType()
            : managedAccount.getName(),
        managedAccount.getClientId(),
        managedAccount.getAppKey(),
        managedAccount.getTenantId(),
        managedAccount.getSubscriptionId(),
        managedAccount.getRegions(),
        managedAccount.getVmImages(),
        managedAccount.getCustomImages(),
        managedAccount.getDefaultResourceGroup(),
        managedAccount.getDefaultKeyVault(),
        managedAccount.getUseSshPublicKey(),
        "clouddriver",
        permissions,
        null);
  }
}
