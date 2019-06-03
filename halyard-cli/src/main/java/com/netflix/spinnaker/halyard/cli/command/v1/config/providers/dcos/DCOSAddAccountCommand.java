package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

import static java.util.Objects.isNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount.ClusterCredential;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class DCOSAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  @Parameter(
      names = "--docker-registries",
      description = DCOSCommandProperties.DOCKER_REGISTRIES_DESCRIPTION,
      required = true,
      variableArity = true)
  private List<String> dockerRegistries = new ArrayList<>();

  @Parameter(names = "--uid", description = "User or service account identifier", required = true)
  private String uid;

  @Parameter(
      names = "--cluster",
      description =
          "Reference to the name of the cluster from the set of clusters defined for this provider",
      required = true)
  private String cluster;

  @Parameter(
      names = "--service-key-file",
      converter = LocalFileConverter.class,
      description = "Path to a file containing the secret key for service account authentication")
  private String serviceKeyFile;

  @Parameter(names = "--password", description = "Password for a user account")
  private String password;

  @Override
  protected Account buildAccount(String accountName) {
    DCOSAccount account = (DCOSAccount) new DCOSAccount().setName(accountName);
    dockerRegistries.forEach(
        registryName ->
            account
                .getDockerRegistries()
                .add(new DockerRegistryReference().setAccountName(registryName)));

    if (!isNull(serviceKeyFile) && !isNull(password)) {
      throw new IllegalArgumentException("Only one of --service-key-file or --password may be set");
    }

    account.setClusters(
        Lists.newArrayList(new ClusterCredential(cluster, uid, password, serviceKeyFile)));

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new DCOSAccount();
  }
}
