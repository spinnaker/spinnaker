/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.proxmox.security;

import static lombok.EqualsAndHashCode.Include;

import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.config.ProxmoxConfigurationProperties;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import it.corsinvest.proxmoxve.api.PveClient;
import it.corsinvest.proxmoxve.api.PveExceptionAuthentication;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.util.ObjectUtils;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ParametersAreNonnullByDefault
public class ProxmoxNamedAccountCredentials extends AbstractAccountCredentials<PveClient> {
  private final String cloudProvider = "proxmox";

  @Nonnull @Include private final String name;

  private final String environment;
  private final String accountType;
  private final ProxmoxConfigurationProperties.ProxmoxManagedAccount managedAccount;

  private final Permissions permissions;

  public ProxmoxNamedAccountCredentials(
      ProxmoxConfigurationProperties.ProxmoxManagedAccount managedAccount) {
    this.name = Objects.requireNonNull(managedAccount.getName());
    this.environment = "prod";
    this.accountType = "main";
    this.managedAccount = managedAccount;
    this.permissions = new Permissions.Builder().set(managedAccount.getPermissions()).build();
  }

  /**
   * This method is deprecated and users should instead supply {@link
   * ProxmoxNamedAccountCredentials#permissions}. In order to continue to support users who have
   * `requiredGroupMembership` in their account config, we still need to override this method. We'll
   * need to either communicate the backwards-incompatible change or translate the supplied
   * `requiredGroupMembership` into {@link ProxmoxNamedAccountCredentials#permissions} before
   * removing this override.
   */
  @Override
  @SuppressWarnings("deprecation")
  public List<String> getRequiredGroupMembership() {
    return Collections.emptyList();
  }

  @Override
  public PveClient getCredentials() {
    PveClient client = new PveClient(managedAccount.getServer(), managedAccount.getPort());
    if (managedAccount.isInsecure()) {
      client.setValidateCertificate(false);
    }
    if (!ObjectUtils.isEmpty(managedAccount.getApiToken())) {
      client.setApiToken(managedAccount.getApiToken());
    } else {
      try {
        client.login(managedAccount.getUserName(), managedAccount.getPassword());
      } catch (PveExceptionAuthentication e) {
        throw new RuntimeException(e);
      }
    }
    return client;
  }
}
