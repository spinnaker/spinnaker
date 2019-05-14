/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.LinkedList;
import java.util.List;

public class EcsAccountBuilder {

  public static CredentialsConfig.Account build(
      NetflixAmazonCredentials netflixAmazonCredentials, String accountName, String accountType) {
    CredentialsConfig.Account account = new CredentialsConfig.Account();
    account.setName(accountName);
    account.setAccountType(accountType);
    account.setAccountId(netflixAmazonCredentials.getAccountId());
    account.setAllowPrivateThirdPartyImages(
        netflixAmazonCredentials.getAllowPrivateThirdPartyImages());
    account.setBastionEnabled(netflixAmazonCredentials.getBastionEnabled());
    account.setBastionHost(netflixAmazonCredentials.getBastionHost());
    account.setEdda(account.getEdda());

    account.setDiscoveryEnabled(netflixAmazonCredentials.getDiscoveryEnabled());
    account.setDiscovery(netflixAmazonCredentials.getDiscovery());
    account.setDefaultKeyPair(netflixAmazonCredentials.getDefaultKeyPair());
    account.setDefaultSecurityGroups(netflixAmazonCredentials.getDefaultSecurityGroups());
    account.setEddaEnabled(netflixAmazonCredentials.getEddaEnabled());
    account.setEnvironment(netflixAmazonCredentials.getEnvironment());
    account.setFront50(netflixAmazonCredentials.getFront50());
    account.setFront50Enabled(netflixAmazonCredentials.getFront50Enabled());
    account.setRequiredGroupMembership(netflixAmazonCredentials.getRequiredGroupMembership());

    // TODO - The lines below should be conditional on having an AssumeRole
    if (netflixAmazonCredentials instanceof NetflixAssumeRoleAmazonCredentials
        && ((NetflixAssumeRoleAmazonCredentials) netflixAmazonCredentials).getAssumeRole()
            != null) {
      account.setSessionName(
          ((NetflixAssumeRoleAmazonCredentials) netflixAmazonCredentials).getSessionName());
      account.setAssumeRole(
          ((NetflixAssumeRoleAmazonCredentials) netflixAmazonCredentials).getAssumeRole());
    }

    List<CredentialsConfig.Region> regions = new LinkedList<>();
    for (AmazonCredentials.AWSRegion awsRegion : netflixAmazonCredentials.getRegions()) {
      CredentialsConfig.Region region = new CredentialsConfig.Region();
      region.setAvailabilityZones((List<String>) awsRegion.getAvailabilityZones());
      region.setDeprecated(awsRegion.getDeprecated());
      region.setName(awsRegion.getName());
      region.setPreferredZones((List<String>) awsRegion.getPreferredZones());
      regions.add(region);
    }
    account.setRegions(regions);

    List<CredentialsConfig.LifecycleHook> hooks = new LinkedList<>();
    for (AmazonCredentials.LifecycleHook awsHook : netflixAmazonCredentials.getLifecycleHooks()) {
      CredentialsConfig.LifecycleHook hook = new CredentialsConfig.LifecycleHook();
      hook.setDefaultResult(awsHook.getDefaultResult());
      hook.setHeartbeatTimeout(awsHook.getHeartbeatTimeout());
      hook.setLifecycleTransition(awsHook.getLifecycleTransition());
      hook.setNotificationTargetARN(awsHook.getNotificationTargetARN());
      hook.setRoleARN(awsHook.getRoleARN());
    }
    account.setLifecycleHooks(hooks);

    Permissions.Builder permBuilder = new Permissions.Builder();
    for (String group : netflixAmazonCredentials.getPermissions().allGroups()) {
      List<String> roles = new LinkedList<>();
      roles.add(group);
      for (Authorization auth :
          netflixAmazonCredentials.getPermissions().getAuthorizations(roles)) {
        permBuilder.add(auth, group);
      }
    }
    account.setPermissions(permBuilder);

    return account;
  }
}
