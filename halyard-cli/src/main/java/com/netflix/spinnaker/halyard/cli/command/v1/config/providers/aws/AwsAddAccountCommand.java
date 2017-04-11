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
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Parameters(separators = "=")
public class AwsAddAccountCommand extends AbstractAddAccountCommand {
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
  private List<String> regions = new ArrayList<>();

  @Override
  protected Account buildAccount(String accountName) {
    AwsAccount account = (AwsAccount) new AwsAccount().setName(accountName);
    account.setDefaultKeyPair(defaultKeyPair)
        .setEdda(edda)
        .setDiscovery(discovery)
        .setAccountId(accountId)
        .setRegions(regions
            .stream()
            .map(r -> new AwsAccount.AwsRegion().setName(r))
            .collect(Collectors.toList())
        );

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new AwsAccount();
  }
}
