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
import com.netflix.spinnaker.fiat.model.resources.Permissions;

import java.util.List;

/**
 *
 * @see AssumeRoleAmazonCredentials
 */
public class NetflixAssumeRoleAmazonCredentials extends NetflixAmazonCredentials {

    /**
     * The role to assume on the target account.
     */
    private final String assumeRole;
    private final String sessionName;

    public NetflixAssumeRoleAmazonCredentials(@JsonProperty("name") String name,
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
                                              @JsonProperty("assumeRole") String assumeRole,
                                              @JsonProperty("sessionName") String sessionName,
                                              @JsonProperty("lambdaEnabled") Boolean lambdaEnabled) {

        this(name,
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
             assumeRole,
             sessionName,
             lambdaEnabled);
    }

    public NetflixAssumeRoleAmazonCredentials(NetflixAssumeRoleAmazonCredentials copy, AWSCredentialsProvider credentialsProvider) {
        this(copy.getName(),
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
             copy.getEddaEnabled(),
             copy.getDiscovery(),
             copy.getDiscoveryEnabled(),
             copy.getFront50(),
             copy.getFront50Enabled(),
             copy.getBastionHost(),
             copy.getBastionEnabled(),
             copy.getShieldEnabled(),
             copy.getAssumeRole(),
             copy.getSessionName(),
             copy.getLambdaEnabled());
    }

    NetflixAssumeRoleAmazonCredentials(String name,
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
                                       String assumeRole,
                                       String sessionName,
                                       Boolean lambdaEnabled) {
        super(name,
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
              AssumeRoleAmazonCredentials.createSTSCredentialsProvider(credentialsProvider,
                                                                       accountId,
                                                                       assumeRole,
                                                                       sessionName == null ? AssumeRoleAmazonCredentials.DEFAULT_SESSION_NAME : sessionName),
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
        this.assumeRole = assumeRole;
        this.sessionName = sessionName == null ? AssumeRoleAmazonCredentials.DEFAULT_SESSION_NAME : sessionName;
    }

    public String getAssumeRole() {
        return assumeRole;
    }

    public String getSessionName() {
        return sessionName;
    }
}
