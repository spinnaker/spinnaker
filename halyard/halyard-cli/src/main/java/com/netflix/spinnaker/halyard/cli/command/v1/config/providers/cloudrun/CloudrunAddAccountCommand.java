package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudrun;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudrun.CloudrunAccount;

@Parameters(separators = "=")
public class CloudrunAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "cloudrun";
  }

  @Parameter(
      names = "--project",
      required = true,
      description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(
      names = "--local-repository-directory",
      description = CloudrunCommandProperties.LOCAL_REPOSITORY_DIRECTORY_DESCRIPTION)
  private String localRepositoryDirectory = "/var/tmp/clouddriver";

  @Parameter(
      names = "--ssh-trust-unknown-hosts",
      description = CloudrunCommandProperties.SSH_TRUST_UNKNOWN_HOSTS,
      arity = 1)
  private boolean sshTrustUnknownHosts = false;

  @Override
  protected Account buildAccount(String accountName) {
    CloudrunAccount account = (CloudrunAccount) new CloudrunAccount().setName(accountName);
    account.setProject(project).setJsonPath(jsonPath);

    account
        .setLocalRepositoryDirectory(localRepositoryDirectory)
        .setSshTrustUnknownHosts(sshTrustUnknownHosts);
    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new CloudrunAccount();
  }
}
