/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.config;

import static lombok.EqualsAndHashCode.Include;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.EqualsAndHashCode;

/**
 * Previously, accounts were stored in the {@link CredentialsConfig} class. If there are loads of
 * accounts defined in a configuration properties file, then letting Spring boot read and bind them
 * is a fairly time-consuming process. For 400 accounts, we observed that it took ~3-5m to load
 * them, with the variance depending on the node configuration.
 *
 * <p>To speed this up, a feature-flagged change will be introduced in a follow up PR to let us do a
 * manual binding of the properties directly, instead of letting spring boot do it. This results in
 * the load times dropping to ~1-2s. But the main drawback of this manual binding is the fact that
 * we have to explicitly define all the properties that we need to bind. For example, if accounts
 * are defined in one configuration file and the other properties are defined in a different file,
 * those other properties will not be loaded unless they are defined in the same configuration file.
 * Also, for that to work, we have to explicitly bind these properties to the target class.
 *
 * <p>By moving accounts out of the {@link CredentialsConfig} class, we don't need to do any manual
 * binding for those other properties. And we do the manual binding for accounts only, which makes
 * it more maintainable.
 */
public class AccountsConfiguration {

  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @JsonTypeName("aws")
  public static class Account implements AccessControlledAccountDefinition {
    @Include private String name;
    @Include private String environment;
    @Include private String accountType;
    @Include private String accountId;
    @Include private String defaultKeyPair;
    @Include private Boolean enabled;
    @Include private List<CredentialsConfig.Region> regions;
    @Include private List<String> defaultSecurityGroups;
    private List<String> requiredGroupMembership;
    @Include private Permissions.Builder permissions;
    @Include private String edda;
    @Include private Boolean eddaEnabled;
    @Include private Boolean lambdaEnabled;
    @Include private String discovery;
    @Include private Boolean discoveryEnabled;
    @Include private String front50;
    @Include private Boolean front50Enabled;
    @Include private String bastionHost;
    @Include private Boolean bastionEnabled;
    @Include private String assumeRole;
    @Include private String sessionName;
    @Include private Integer sessionDurationSeconds;
    @Include private String externalId;
    @Include private List<CredentialsConfig.LifecycleHook> lifecycleHooks;
    @Include private boolean allowPrivateThirdPartyImages;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEnvironment() {
      return environment;
    }

    public void setEnvironment(String environment) {
      this.environment = environment;
    }

    public String getAccountType() {
      return accountType;
    }

    public void setAccountType(String accountType) {
      this.accountType = accountType;
    }

    public String getAccountId() {
      return accountId;
    }

    public void setAccountId(String accountId) {
      this.accountId = accountId;
    }

    public String getDefaultKeyPair() {
      return defaultKeyPair;
    }

    public void setDefaultKeyPair(String defaultKeyPair) {
      this.defaultKeyPair = defaultKeyPair;
    }

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public List<CredentialsConfig.Region> getRegions() {
      return regions;
    }

    public void setRegions(List<CredentialsConfig.Region> regions) {
      if (regions != null) {
        regions.sort(Comparator.comparing(CredentialsConfig.Region::getName));
      }
      this.regions = regions;
    }

    public List<String> getDefaultSecurityGroups() {
      return defaultSecurityGroups;
    }

    public void setDefaultSecurityGroups(List<String> defaultSecurityGroups) {
      if (defaultSecurityGroups != null) {
        Collections.sort(defaultSecurityGroups);
      }
      this.defaultSecurityGroups = defaultSecurityGroups;
    }

    public List<String> getRequiredGroupMembership() {
      return requiredGroupMembership;
    }

    public void setRequiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership;
    }

    public Permissions.Builder getPermissions() {
      return permissions;
    }

    public void setPermissions(Permissions.Builder permissions) {
      this.permissions = permissions;
    }

    public String getEdda() {
      return edda;
    }

    public void setEdda(String edda) {
      this.edda = edda;
    }

    public Boolean getEddaEnabled() {
      return eddaEnabled;
    }

    public void setEddaEnabled(Boolean eddaEnabled) {
      this.eddaEnabled = eddaEnabled;
    }

    public String getDiscovery() {
      return discovery;
    }

    public void setDiscovery(String discovery) {
      this.discovery = discovery;
    }

    public Boolean getDiscoveryEnabled() {
      return discoveryEnabled;
    }

    public void setDiscoveryEnabled(Boolean discoveryEnabled) {
      this.discoveryEnabled = discoveryEnabled;
    }

    public String getFront50() {
      return front50;
    }

    public void setFront50(String front50) {
      this.front50 = front50;
    }

    public Boolean getFront50Enabled() {
      return front50Enabled;
    }

    public void setFront50Enabled(Boolean front50Enabled) {
      this.front50Enabled = front50Enabled;
    }

    public String getBastionHost() {
      return bastionHost;
    }

    public void setBastionHost(String bastionHost) {
      this.bastionHost = bastionHost;
    }

    public Boolean getBastionEnabled() {
      return bastionEnabled;
    }

    public void setBastionEnabled(Boolean bastionEnabled) {
      this.bastionEnabled = bastionEnabled;
    }

    public String getAssumeRole() {
      return assumeRole;
    }

    public void setAssumeRole(String assumeRole) {
      this.assumeRole = assumeRole;
    }

    public String getSessionName() {
      return sessionName;
    }

    public void setSessionName(String sessionName) {
      this.sessionName = sessionName;
    }

    public Integer getSessionDurationSeconds() {
      return sessionDurationSeconds;
    }

    public void setSessionDurationSeconds(Integer sessionDurationSeconds) {
      this.sessionDurationSeconds = sessionDurationSeconds;
    }

    public String getExternalId() {
      return externalId;
    }

    public void setExternalId(String externalId) {
      this.externalId = externalId;
    }

    public List<CredentialsConfig.LifecycleHook> getLifecycleHooks() {
      return lifecycleHooks;
    }

    public void setLifecycleHooks(List<CredentialsConfig.LifecycleHook> lifecycleHooks) {
      this.lifecycleHooks = lifecycleHooks;
    }

    public Boolean getAllowPrivateThirdPartyImages() {
      return allowPrivateThirdPartyImages;
    }

    public void setAllowPrivateThirdPartyImages(Boolean allowPrivateThirdPartyImages) {
      this.allowPrivateThirdPartyImages = allowPrivateThirdPartyImages;
    }

    public Boolean getLambdaEnabled() {
      return lambdaEnabled;
    }

    public void setLambdaEnabled(Boolean lambdaEnabled) {
      this.lambdaEnabled = lambdaEnabled;
    }
  }

  private List<Account> accounts;

  public List<Account> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<Account> accounts) {
    this.accounts = accounts;
  }
}
