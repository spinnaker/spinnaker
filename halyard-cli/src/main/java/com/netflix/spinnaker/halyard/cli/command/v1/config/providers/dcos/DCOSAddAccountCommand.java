package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount.ClusterCredential;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DockerRegistryReference;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;

@Parameters(separators = "=")
public class DCOSAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  @Parameter(
      names = "--docker-registries",
      description = DCOSCommandProperties.DOCKER_REGISTRIES_DESCRIPTION,
      required = true,
      variableArity = true
  )
  private List<String> dockerRegistries = new ArrayList<>();

  @Parameter(
      names = "--uid",
      description = "User or service account identifier",
      required = true
  )
  private String uid;

  @Parameter(
      names = "--cluster",
      description = "Reference to the name of the cluster from the set of clusters defined for this provider",
      required = true

  )
  private String cluster;

  //TODO(willgorman): might be good to support loading from a file
  @Parameter(
      names = "--service-key",
      description = "Secret key for service account authentication"
  )
  private String serviceKey;

  @Parameter(
      names = "--password",
      description = "Password for a user account"
  )
  private String password;

  @Override
  protected Account buildAccount(String accountName) {
    DCOSAccount account = (DCOSAccount) new DCOSAccount().setName(accountName);
    dockerRegistries.forEach(registryName -> account.getDockerRegistries().add(new DockerRegistryReference().setAccountName(registryName)));

    if (!isNull(serviceKey) && !isNull(password)) {
      throw new IllegalArgumentException("Only one of --service-key or --password may be set");
    }

    account.setClusters(Lists.newArrayList(new ClusterCredential(cluster, uid, password, serviceKey)));

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new DCOSAccount();
  }
}
