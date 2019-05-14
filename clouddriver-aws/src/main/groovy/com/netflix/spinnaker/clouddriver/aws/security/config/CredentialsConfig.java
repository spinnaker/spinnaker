/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.List;

/**
 * A mutable credentials configurations structure suitable for transformation into concrete
 * credentials implementations.
 */
public class CredentialsConfig {
  public static class Region {
    private String name;
    private List<String> availabilityZones;
    private List<String> preferredZones;
    private Boolean deprecated;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getAvailabilityZones() {
      return availabilityZones;
    }

    public void setAvailabilityZones(List<String> availabilityZones) {
      this.availabilityZones = availabilityZones;
    }

    public List<String> getPreferredZones() {
      return preferredZones;
    }

    public void setPreferredZones(List<String> preferredZones) {
      this.preferredZones = preferredZones;
    }

    public Boolean getDeprecated() {
      return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
      this.deprecated = deprecated;
    }

    Region copyOf() {
      Region clone = new Region();
      clone.setName(getName());
      clone.setAvailabilityZones(getAvailabilityZones());
      clone.setPreferredZones(getPreferredZones());
      clone.setDeprecated(getDeprecated());

      return clone;
    }
  }

  public static class LifecycleHook {
    private String name;
    private String roleARN;
    private String notificationTargetARN;
    private String lifecycleTransition;
    private Integer heartbeatTimeout;
    private String defaultResult;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getRoleARN() {
      return roleARN;
    }

    public void setRoleARN(String roleARN) {
      this.roleARN = roleARN;
    }

    public String getNotificationTargetARN() {
      return notificationTargetARN;
    }

    public void setNotificationTargetARN(String notificationTargetARN) {
      this.notificationTargetARN = notificationTargetARN;
    }

    public String getLifecycleTransition() {
      return lifecycleTransition;
    }

    public void setLifecycleTransition(String lifecycleTransition) {
      this.lifecycleTransition = lifecycleTransition;
    }

    public Integer getHeartbeatTimeout() {
      return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(Integer heartbeatTimeout) {
      this.heartbeatTimeout = heartbeatTimeout;
    }

    public String getDefaultResult() {
      return defaultResult;
    }

    public void setDefaultResult(String defaultResult) {
      this.defaultResult = defaultResult;
    }
  }

  public static class Account {
    private String name;
    private String environment;
    private String accountType;
    private String accountId;
    private String defaultKeyPair;
    private Boolean enabled;
    private List<Region> regions;
    private List<String> defaultSecurityGroups;
    private List<String> requiredGroupMembership;
    private Permissions.Builder permissions;
    private String edda;
    private Boolean eddaEnabled;
    private Boolean lambdaEnabled;
    private String discovery;
    private Boolean discoveryEnabled;
    private String front50;
    private Boolean front50Enabled;
    private String bastionHost;
    private Boolean bastionEnabled;
    private String assumeRole;
    private String sessionName;
    private List<LifecycleHook> lifecycleHooks;
    private boolean allowPrivateThirdPartyImages;

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

    public List<Region> getRegions() {
      return regions;
    }

    public void setRegions(List<Region> regions) {
      this.regions = regions;
    }

    public List<String> getDefaultSecurityGroups() {
      return defaultSecurityGroups;
    }

    public void setDefaultSecurityGroups(List<String> defaultSecurityGroups) {
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

    public List<LifecycleHook> getLifecycleHooks() {
      return lifecycleHooks;
    }

    public void setLifecycleHooks(List<LifecycleHook> lifecycleHooks) {
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

  private String defaultKeyPairTemplate;
  private List<Region> defaultRegions;
  private List<String> defaultSecurityGroups;
  private List<LifecycleHook> defaultLifecycleHooks;
  private String defaultEddaTemplate;
  private String defaultFront50Template;
  private String defaultBastionHostTemplate;
  private String defaultDiscoveryTemplate;
  private String defaultAssumeRole;
  private String defaultSessionName;
  private String defaultLifecycleHookRoleARNTemplate;
  private String defaultLifecycleHookNotificationTargetARNTemplate;

  private List<Account> accounts;

  public String getDefaultKeyPairTemplate() {
    return defaultKeyPairTemplate;
  }

  public void setDefaultKeyPairTemplate(String defaultKeyPairTemplate) {
    this.defaultKeyPairTemplate = defaultKeyPairTemplate;
  }

  public List<Region> getDefaultRegions() {
    return defaultRegions;
  }

  public void setDefaultRegions(List<Region> defaultRegions) {
    this.defaultRegions = defaultRegions;
  }

  public List<String> getDefaultSecurityGroups() {
    return defaultSecurityGroups;
  }

  public void setDefaultSecurityGroups(List<String> defaultSecurityGroups) {
    this.defaultSecurityGroups = defaultSecurityGroups;
  }

  public String getDefaultEddaTemplate() {
    return defaultEddaTemplate;
  }

  public void setDefaultEddaTemplate(String defaultEddaTemplate) {
    this.defaultEddaTemplate = defaultEddaTemplate;
  }

  public String getDefaultFront50Template() {
    return defaultFront50Template;
  }

  public void setDefaultFront50Template(String defaultFront50Template) {
    this.defaultFront50Template = defaultFront50Template;
  }

  public String getDefaultBastionHostTemplate() {
    return defaultBastionHostTemplate;
  }

  public void setDefaultBastionHostTemplate(String defaultBastionHostTemplate) {
    this.defaultBastionHostTemplate = defaultBastionHostTemplate;
  }

  public String getDefaultDiscoveryTemplate() {
    return defaultDiscoveryTemplate;
  }

  public void setDefaultDiscoveryTemplate(String defaultDiscoveryTemplate) {
    this.defaultDiscoveryTemplate = defaultDiscoveryTemplate;
  }

  public String getDefaultAssumeRole() {
    return defaultAssumeRole;
  }

  public void setDefaultAssumeRole(String defaultAssumeRole) {
    this.defaultAssumeRole = defaultAssumeRole;
  }

  public String getDefaultSessionName() {
    return defaultSessionName;
  }

  public void setDefaultSessionName(String defaultSessionName) {
    this.defaultSessionName = defaultSessionName;
  }

  public List<Account> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<Account> accounts) {
    this.accounts = accounts;
  }

  public List<LifecycleHook> getDefaultLifecycleHooks() {
    return defaultLifecycleHooks;
  }

  public void setDefaultLifecycleHooks(List<LifecycleHook> defaultLifecycleHooks) {
    this.defaultLifecycleHooks = defaultLifecycleHooks;
  }

  public String getDefaultLifecycleHookRoleARNTemplate() {
    return defaultLifecycleHookRoleARNTemplate;
  }

  public void setDefaultLifecycleHookRoleARNTemplate(String defaultLifecycleHookRoleARNTemplate) {
    this.defaultLifecycleHookRoleARNTemplate = defaultLifecycleHookRoleARNTemplate;
  }

  public String getDefaultLifecycleHookNotificationTargetARNTemplate() {
    return defaultLifecycleHookNotificationTargetARNTemplate;
  }

  public void setDefaultLifecycleHookNotificationTargetARNTemplate(
      String defaultLifecycleHookNotificationTargetARNTemplate) {
    this.defaultLifecycleHookNotificationTargetARNTemplate =
        defaultLifecycleHookNotificationTargetARNTemplate;
  }
}
