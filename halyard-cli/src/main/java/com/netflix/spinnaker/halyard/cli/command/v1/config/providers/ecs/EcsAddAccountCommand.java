package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ecs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsAccount;

@Parameters(separators = "=")
public class EcsAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "ecs";
  }

  @Parameter(
      names = "--aws-account",
      required = true,
      description = EcsCommandProperties.AWS_ACCOUNT_DESCRIPTION)
  private String awsAccount;

  @Override
  protected Account buildAccount(String accountName) {
    EcsAccount account = (EcsAccount) new EcsAccount().setName(accountName);
    account.setAwsAccount(awsAccount);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new EcsAccount();
  }
}
