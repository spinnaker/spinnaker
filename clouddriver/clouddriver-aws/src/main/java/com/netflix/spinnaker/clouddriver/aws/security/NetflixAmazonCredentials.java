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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.List;
import lombok.Getter;

/**
 * An implementation of {@link AmazonCredentials} that is decorated with Netflix concepts like
 * Discovery, Front50,
 */
@Getter
public class NetflixAmazonCredentials extends AmazonCredentials {
  private final String discovery;
  private final boolean discoveryEnabled;
  private final String front50;
  private final boolean front50Enabled;
  private final boolean shieldEnabled;
  private final boolean lambdaEnabled;

  public NetflixAmazonCredentials(
      @JsonProperty("name") String name,
      @JsonProperty("environment") String environment,
      @JsonProperty("accountType") String accountType,
      @JsonProperty("accountId") String accountId,
      @JsonProperty("defaultKeyPair") String defaultKeyPair,
      @JsonProperty("enabled") Boolean enabled,
      @JsonProperty("regions") List<AWSRegion> regions,
      @JsonProperty("defaultSecurityGroups") List<String> defaultSecurityGroups,
      @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership,
      @JsonProperty("permissions") Permissions permissions,
      @JsonProperty("lifecycleHooks") List<LifecycleHook> lifecycleHooks,
      @JsonProperty("allowPrivateThirdPartyImages") boolean allowPrivateThirdPartyImages,
      @JsonProperty("discovery") String discovery,
      @JsonProperty("discoveryEnabled") Boolean discoveryEnabled,
      @JsonProperty("front50") String front50,
      @JsonProperty("front50Enabled") Boolean front50Enabled,
      @JsonProperty("shieldEnabled") Boolean shieldEnabled,
      @JsonProperty("lambdaEnabled") Boolean lambdaEnabled) {
    this(
        name,
        environment,
        accountType,
        accountId,
        defaultKeyPair,
        enabled,
        regions,
        defaultSecurityGroups,
        requiredGroupMembership,
        permissions,
        lifecycleHooks,
        allowPrivateThirdPartyImages,
        null,
        discovery,
        discoveryEnabled,
        front50,
        front50Enabled,
        shieldEnabled,
        lambdaEnabled);
  }

  private static boolean flagValue(String serviceUrl, Boolean flag) {
    return (!(serviceUrl == null || serviceUrl.trim().length() == 0)
        && (flag != null ? flag : true));
  }

  /**
   * Construct a new NetflixAmazonCredentials object by copying an existing one. Even though
   * NetflixAmazonCredentials objects have (via AmazonCredentials) both a credentialsProvider and
   * awsConfigurationProperties, this method takes those as separate arguments in case the existing
   * object doesn't have them, which is the case when it was constructed via deserialization. This
   * is what AmazonCredentialsParser does.
   *
   * @param copy the object to copy
   * @param credentialsProvider a credentials provider
   * @param awsConfigurationProperties configuration properties
   */
  public NetflixAmazonCredentials(
      NetflixAmazonCredentials copy,
      AWSCredentialsProvider credentialsProvider,
      AwsConfigurationProperties awsConfigurationProperties) {
    this(
        copy.getName(),
        copy.getEnvironment(),
        copy.getAccountType(),
        copy.getAccountId(),
        copy.getDefaultKeyPair(),
        copy.isEnabled(),
        copy.getRegions(),
        copy.getDefaultSecurityGroups(),
        copy.getRequiredGroupMembership(),
        copy.getPermissions(),
        copy.getLifecycleHooks(),
        copy.getAllowPrivateThirdPartyImages(),
        credentialsProvider,
        copy.getDiscovery(),
        copy.isDiscoveryEnabled(),
        copy.getFront50(),
        copy.isFront50Enabled(),
        copy.isShieldEnabled(),
        copy.isLambdaEnabled());
  }

  NetflixAmazonCredentials(
      String name,
      String environment,
      String accountType,
      String accountId,
      String defaultKeyPair,
      Boolean enabled,
      List<AWSRegion> regions,
      List<String> defaultSecurityGroups,
      List<String> requiredGroupMembership,
      Permissions permissions,
      List<LifecycleHook> lifecycleHooks,
      boolean allowPrivateThirdPartyImages,
      AWSCredentialsProvider credentialsProvider,
      String discovery,
      Boolean discoveryEnabled,
      String front50,
      Boolean front50Enabled,
      Boolean shieldEnabled,
      Boolean lambdaEnabled) {
    super(
        name,
        environment,
        accountType,
        accountId,
        defaultKeyPair,
        enabled,
        regions,
        defaultSecurityGroups,
        requiredGroupMembership,
        permissions,
        lifecycleHooks,
        allowPrivateThirdPartyImages,
        credentialsProvider);
    this.discovery = discovery;
    this.discoveryEnabled = flagValue(discovery, discoveryEnabled);
    this.front50 = front50;
    this.front50Enabled = flagValue(front50, front50Enabled);
    this.shieldEnabled = shieldEnabled != null && shieldEnabled;
    this.lambdaEnabled = lambdaEnabled != null && lambdaEnabled;
  }
}
