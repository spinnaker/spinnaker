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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class LaunchTemplateRollOutConfig {
  private DynamicConfigService dynamicConfigService;

  public LaunchTemplateRollOutConfig(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  public boolean isIpv6EnabledForEnv(String env) {
    return dynamicConfigService.isEnabled("aws.features.launch-templates.ipv6." + env, false);
  }

  /** This is used to gradually roll out launch template. */
  public boolean shouldUseLaunchTemplateForReq(
      String applicationInReq, NetflixAmazonCredentials credentialsInReq, String regionInReq) {

    // Property flag to turn off launch template feature. Caching agent might require bouncing the
    // java process
    if (!dynamicConfigService.isEnabled("aws.features.launch-templates", false)) {
      log.debug("Launch Template feature disabled via configuration.");
      return false;
    }

    // This is a comma separated list of applications to exclude
    String excludedApps =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.excluded-applications", "");
    for (String excludedApp : excludedApps.split(",")) {
      if (excludedApp.trim().equals(applicationInReq)) {
        return false;
      }
    }

    // This is a comma separated list of accounts to exclude
    String excludedAccounts =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.excluded-accounts", "");
    for (String excludedAccount : excludedAccounts.split(",")) {
      if (excludedAccount.trim().equals(credentialsInReq.getName())) {
        return false;
      }
    }

    // Allows everything that is not excluded
    if (dynamicConfigService.isEnabled("aws.features.launch-templates.all-applications", false)) {
      return true;
    }

    // Application allow list with the following format:
    // app1:account:region1,app2:account:region1
    // This allows more control over what account and region pairs to enable for this deployment.
    String allowedApps =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-applications", "");
    if (matchesAppAccountAndRegion(
        applicationInReq, credentialsInReq.getName(), regionInReq, allowedApps.split(","))) {
      return true;
    }

    // An allow list for account/region pairs with the following format:
    // account:region
    String allowedAccountsAndRegions =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-accounts-regions", "");
    for (String accountRegion : allowedAccountsAndRegions.split(",")) {
      if (StringUtils.isNotBlank(accountRegion) && accountRegion.contains(":")) {
        String[] parts = accountRegion.split(":");
        String account = parts[0];
        String region = parts[1];
        if (account.trim().equals(credentialsInReq.getName())
            && region.trim().equals(regionInReq)) {
          return true;
        }
      }
    }

    // This is a comma separated list of accounts to allow
    String allowedAccounts =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-accounts", "");
    for (String allowedAccount : allowedAccounts.split(",")) {
      if (allowedAccount.trim().equals(credentialsInReq.getName())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Helper function to parse and match an array of app:account:region1,...,app:account,region to
   * the specified application, account and region Used to flag launch template feature and rollout
   */
  @VisibleForTesting
  private boolean matchesAppAccountAndRegion(
      String application, String accountName, String region, String[] applicationAccountRegions) {
    if (applicationAccountRegions != null && applicationAccountRegions.length <= 0) {
      return false;
    }

    for (String appAccountRegion : applicationAccountRegions) {
      if (StringUtils.isNotBlank(appAccountRegion) && appAccountRegion.contains(":")) {
        try {
          String[] parts = appAccountRegion.split(":");
          String app = parts[0];
          String account = parts[1];
          String regions = parts[2];
          if (app.equals(application)
              && account.equals(accountName)
              && Arrays.asList(regions.split(",")).contains(region)) {
            return true;
          }
        } catch (Exception e) {
          log.error(
              "Unable to verify if application is allowed in shouldSetLaunchTemplate: {}",
              appAccountRegion);
          return false;
        }
      }
    }

    return false;
  }
}
