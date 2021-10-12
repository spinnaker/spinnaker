/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws;

import static com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws.AwsLifecycleHookUtil.getLifecycleHook;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider.AwsLifecycleHook;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Parameters(separators = "=")
public class AwsEditAccountCommand extends AbstractEditAccountCommand<AwsAccount> {
  protected String getProviderName() {
    return "aws";
  }

  @Parameter(
      names = "--default-key-pair",
      description = AwsCommandProperties.DEFAULT_KEY_PAIR_DESCRIPTION)
  private String defaultKeyPair;

  @Parameter(names = "--edda", description = AwsCommandProperties.EDDA_DESCRIPTION)
  private String edda;

  @Parameter(names = "--discovery", description = AwsCommandProperties.DISCOVERY_DESCRIPTION)
  private String discovery;

  @Parameter(names = "--account-id", description = AwsCommandProperties.ACCOUNT_ID_DESCRIPTION)
  private String accountId;

  @Parameter(
      names = "--regions",
      variableArity = true,
      description = AwsCommandProperties.REGIONS_DESCRIPTION)
  private List<String> regions;

  @Parameter(
      names = "--add-region",
      description = "Add this region to the list of managed regions.")
  private String addRegion;

  @Parameter(
      names = "--remove-region",
      description = "Remove this region from the list of managed regions.")
  private String removeRegion;

  @Parameter(names = "--assume-role", description = AwsCommandProperties.ASSUME_ROLE_DESCRIPTION)
  private String assumeRole;

  @Parameter(names = "--external-id", description = AwsCommandProperties.EXTERNAL_ID_DESCRIPTION)
  private String externalId;

  @Parameter(
      names = "--launching-lifecycle-hook-default-result",
      description = AwsCommandProperties.HOOK_DEFAULT_VALUE_DESCRIPTION)
  private String launchingHookDefaultResult = "ABANDON";

  @Parameter(
      names = "--launching-lifecycle-hook-heartbeat-timeout-seconds",
      description = AwsCommandProperties.HOOK_HEARTBEAT_TIMEOUT_DESCRIPTION)
  private Integer launchingHookHeartbeatTimeoutSeconds = 3600;

  @Parameter(
      names = "--launching-lifecycle-hook-notification-target-arn",
      description = AwsCommandProperties.HOOK_NOTIFICATION_TARGET_ARN)
  private String launchingHookNotificationTargetArn;

  @Parameter(
      names = "--launching-lifecycle-hook-role-arn",
      description = AwsCommandProperties.HOOK_ROLE_ARN_DESCRIPTION)
  private String launchingHookRoleArn;

  @Parameter(
      names = "--terminating-lifecycle-hook-default-result",
      description = AwsCommandProperties.HOOK_DEFAULT_VALUE_DESCRIPTION)
  private String terminatingHookDefaultResult = "ABANDON";

  @Parameter(
      names = "--terminating-lifecycle-hook-heartbeat-timeout-seconds",
      description = AwsCommandProperties.HOOK_HEARTBEAT_TIMEOUT_DESCRIPTION)
  private Integer terminatingHookHeartbeatTimeoutSeconds = 3600;

  @Parameter(
      names = "--terminating-lifecycle-hook-notification-target-arn",
      description = AwsCommandProperties.HOOK_NOTIFICATION_TARGET_ARN)
  private String terminatingHookNotificationTargetArn;

  @Parameter(
      names = "--terminating-lifecycle-hook-role-arn",
      description = AwsCommandProperties.HOOK_ROLE_ARN_DESCRIPTION)
  private String terminatingHookRoleArn;

  @Parameter(
      names = "--lambda-enabled",
      description = AwsCommandProperties.LAMBDA_ENABLED,
      arity = 1)
  private Boolean lambdaEnabled;

  @Override
  protected Account editAccount(AwsAccount account) {
    account.setDefaultKeyPair(isSet(defaultKeyPair) ? defaultKeyPair : account.getDefaultKeyPair());
    account.setEdda(isSet(edda) ? edda : account.getEdda());
    account.setDiscovery(isSet(discovery) ? discovery : account.getDiscovery());
    account.setAccountId(isSet(accountId) ? accountId : account.getAccountId());
    account.setAssumeRole(isSet(assumeRole) ? assumeRole : account.getAssumeRole());
    account.setExternalId(isSet(externalId) ? externalId : account.getExternalId());
    account.setLambdaEnabled(isSet(lambdaEnabled) ? lambdaEnabled : account.getLambdaEnabled());

    List<AwsLifecycleHook> hooks = getLifecycleHooks();
    account.setLifecycleHooks(!hooks.isEmpty() ? hooks : account.getLifecycleHooks());

    try {
      List<String> existingRegions =
          account.getRegions().stream()
              .map(AwsProvider.AwsRegion::getName)
              .collect(Collectors.toList());
      regions = updateStringList(existingRegions, regions, addRegion, removeRegion);
      account.setRegions(
          regions.stream()
              .map(r -> new AwsProvider.AwsRegion().setName(r))
              .collect(Collectors.toList()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --regions or --[add/remove]-region");
    }

    return account;
  }

  private List<AwsLifecycleHook> getLifecycleHooks() {
    Optional<AwsLifecycleHook> initHook =
        getLifecycleHook(
            "autoscaling:EC2_INSTANCE_LAUNCHING",
            launchingHookDefaultResult,
            launchingHookNotificationTargetArn,
            launchingHookRoleArn,
            launchingHookHeartbeatTimeoutSeconds);

    Optional<AwsLifecycleHook> terminatingHook =
        getLifecycleHook(
            "autoscaling:EC2_INSTANCE_TERMINATING",
            terminatingHookDefaultResult,
            terminatingHookNotificationTargetArn,
            terminatingHookRoleArn,
            terminatingHookHeartbeatTimeoutSeconds);

    return Stream.of(initHook, terminatingHook)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }
}
