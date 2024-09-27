package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudrun;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudrun.CloudrunAccount;

@Parameters(separators = "=")
public class CloudrunEditAccountCommand extends AbstractEditAccountCommand<CloudrunAccount> {
  @Override
  protected String getProviderName() {
    return "cloudrun";
  }

  @Parameter(names = "--project", description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(
      names = "--local-repository-directory",
      description = CloudrunCommandProperties.LOCAL_REPOSITORY_DIRECTORY_DESCRIPTION)
  private String localRepositoryDirectory;

  @Parameter(
      names = "--ssh-known-hosts-file-path",
      converter = LocalFileConverter.class,
      description = CloudrunCommandProperties.SSH_KNOWN_HOSTS_FILE_PATH)
  private String sshKnownHostsFilePath;

  @Parameter(
      names = "--ssh-trust-unknown-hosts",
      description = CloudrunCommandProperties.SSH_TRUST_UNKNOWN_HOSTS,
      arity = 1)
  private Boolean sshTrustUnknownHosts = null;

  @Override
  protected Account editAccount(CloudrunAccount account) {
    account.setJsonPath(isSet(jsonPath) ? jsonPath : account.getJsonPath());
    account.setProject(isSet(project) ? project : account.getProject());
    account.setLocalRepositoryDirectory(
        isSet(localRepositoryDirectory)
            ? localRepositoryDirectory
            : account.getLocalRepositoryDirectory());

    account.setSshKnownHostsFilePath(
        isSet(sshKnownHostsFilePath) ? sshKnownHostsFilePath : account.getSshKnownHostsFilePath());
    account.setSshTrustUnknownHosts(
        sshTrustUnknownHosts != null ? sshTrustUnknownHosts : account.isSshTrustUnknownHosts());

    return account;
  }
}
