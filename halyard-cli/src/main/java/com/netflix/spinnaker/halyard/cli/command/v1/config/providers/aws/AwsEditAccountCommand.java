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

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;

import java.util.List;
import java.util.stream.Collectors;

public class AwsEditAccountCommand extends AbstractEditAccountCommand<AwsAccount> {
  protected String getProviderName() {
    return "aws";
  }

  @Parameter(
      names = "--default-key-pair",
      description = AwsCommandProperties.DEFAULT_KEY_PAIR_DESCRIPTION
  )
  private String defaultKeyPair;

  @Parameter(
      names = "--edda",
      description = AwsCommandProperties.EDDA_DESCRIPTION
  )
  private String edda;

  @Parameter(
      names = "--discovery",
      description = AwsCommandProperties.DISCOVERY_DESCRIPTION
  )
  private String discovery;

  @Parameter(
      names = "--account-id",
      description = AwsCommandProperties.ACCOUNT_ID_DESCRIPTION
  )
  private String accountId;

  @Parameter(
      names = "--regions",
      variableArity = true,
      description = AwsCommandProperties.REGIONS_DESCRIPTION
  )
  private List<String> regions;

  @Parameter(
      names = "--add-region",
      description = "Add this region to the list of managed regions."
  )
  private String addRegion;

  @Parameter(
      names = "--remove-region",
      description = "Remove this region from the list of managed regions."
  )
  private String removeRegion;

  @Override
  protected Account editAccount(AwsAccount account) {
    account.setDefaultKeyPair(isSet(defaultKeyPair) ? defaultKeyPair : account.getDefaultKeyPair());
    account.setEdda(isSet(edda) ? edda : account.getEdda());
    account.setDiscovery(isSet(discovery) ? discovery : account.getDiscovery());
    account.setAccountId(isSet(accountId) ? accountId : account.getAccountId());

    try {
      List<String> existingRegions = account
          .getRegions()
          .stream()
          .map(AwsAccount.AwsRegion::getName).collect(Collectors.toList());
      regions = updateStringList(existingRegions, regions, addRegion, removeRegion);
      account.setRegions(regions
              .stream()
              .map(r -> new AwsAccount.AwsRegion().setName(r))
              .collect(Collectors.toList())
          );
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --region or --[add/remove]-region");
    }

    return account;
  }
}
