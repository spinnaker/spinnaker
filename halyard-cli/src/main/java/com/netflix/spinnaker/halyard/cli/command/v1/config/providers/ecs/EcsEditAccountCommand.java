package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ecs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsAccount;

@Parameters(separators = "=")
public class EcsEditAccountCommand extends AbstractEditAccountCommand<EcsAccount> {
  protected String getProviderName() {
    return "ecs";
  }

  @Parameter(names = "--aws-account", description = EcsCommandProperties.AWS_ACCOUNT_DESCRIPTION)
  private String awsAccount;

  @Override
  protected Account editAccount(EcsAccount account) {
    account.setAwsAccount((isSet(awsAccount) ? awsAccount : account.getAwsAccount()));

    return account;
  }
}
