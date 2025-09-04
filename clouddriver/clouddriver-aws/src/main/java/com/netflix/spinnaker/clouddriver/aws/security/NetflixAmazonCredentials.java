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
 * An implementation of {@link AmazonCredentials} that is decorated with Netflix concepts like Edda,
 * Discovery, Front50,
 */
@Getter
public class NetflixAmazonCredentials extends AmazonCredentials {
  private final String edda;
  private final boolean eddaEnabled;
  private final String discovery;
  private final boolean discoveryEnabled;
  private final String front50;
  private final boolean front50Enabled;
  private final String bastionHost;
  private final boolean bastionEnabled;
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
      @JsonProperty("edda") String edda,
      @JsonProperty("eddaEnabled") Boolean eddaEnabled,
      @JsonProperty("discovery") String discovery,
      @JsonProperty("discoveryEnabled") Boolean discoveryEnabled,
      @JsonProperty("front50") String front50,
      @JsonProperty("front50Enabled") Boolean front50Enabled,
      @JsonProperty("bastionHost") String bastionHost,
      @JsonProperty("bastionEnabled") Boolean bastionEnabled,
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
        edda,
        eddaEnabled,
        discovery,
        discoveryEnabled,
        front50,
        front50Enabled,
        bastionHost,
        bastionEnabled,
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
        copy.getEdda(),
        copy.isEddaEnabled(),
        copy.getDiscovery(),
        copy.isDiscoveryEnabled(),
        copy.getFront50(),
        copy.isFront50Enabled(),
        copy.getBastionHost(),
        copy.isBastionEnabled(),
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
      String edda,
      Boolean eddaEnabled,
      String discovery,
      Boolean discoveryEnabled,
      String front50,
      Boolean front50Enabled,
      String bastionHost,
      Boolean bastionEnabled,
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
    this.edda = edda;
    this.eddaEnabled = flagValue(edda, eddaEnabled);
    this.discovery = discovery;
    this.discoveryEnabled = flagValue(discovery, discoveryEnabled);
    this.front50 = front50;
    this.front50Enabled = flagValue(front50, front50Enabled);
    this.bastionHost = bastionHost;
    this.bastionEnabled = flagValue(bastionHost, bastionEnabled);
    this.shieldEnabled = shieldEnabled != null && shieldEnabled;
    this.lambdaEnabled = lambdaEnabled != null && lambdaEnabled;
  }
}
