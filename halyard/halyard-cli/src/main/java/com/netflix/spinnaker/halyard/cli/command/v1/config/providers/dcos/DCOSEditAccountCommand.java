package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notes:
 *
 * <p>* Editing clusters is tricky since the name/uid are like a composite key
 */
@Parameters(separators = "=")
public class DCOSEditAccountCommand extends AbstractEditAccountCommand<DCOSAccount> {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  @Parameter(
      names = "--remove-credential",
      description =
          "Provide the cluster name and uid of credentials to remove: --remove-credential my-cluster my-user",
      arity = 2)
  private List<String> removeCredential = new ArrayList<>();

  @Parameter(
      names = "--update-user-credential",
      description = DCOSCommandProperties.USER_CREDENTIAL,
      variableArity = true)
  private List<String> updateUserCredential = new ArrayList<>();

  @Parameter(
      names = "--update-service-credential",
      description = DCOSCommandProperties.SERVICE_CREDENTIAL,
      variableArity = true)
  private List<String> updateServiceCredential = new ArrayList<>();

  @Parameter(
      names = "--docker-registries",
      variableArity = true,
      description = DCOSCommandProperties.DOCKER_REGISTRIES_DESCRIPTION)
  public List<String> dockerRegistries = new ArrayList<>();

  @Parameter(
      names = "--add-docker-registry",
      description =
          "Add this docker registry to the list of docker registries to use as a source of images.")
  private String addDockerRegistry;

  @Parameter(
      names = "--remove-docker-registry",
      description =
          "Remove this docker registry from the list of docker registries to use as a source of images.")
  private String removeDockerRegistry;

  @Override
  protected Account editAccount(DCOSAccount account) {

    if (!removeCredential.isEmpty()) {
      account.removeCredential(removeCredential.get(0), removeCredential.get(1));
    }

    if (!updateUserCredential.isEmpty()) {
      validateCredential(updateUserCredential);
      final String clusterName = updateUserCredential.get(0);
      final String uid = updateUserCredential.get(1);
      final String password = updateUserCredential.get(2);
      account.removeCredential(clusterName, uid);
      final DCOSAccount.ClusterCredential credential =
          new DCOSAccount.ClusterCredential(clusterName, uid, password, null);
      account.getClusters().add(credential);
    }

    if (!updateServiceCredential.isEmpty()) {
      validateCredential(updateServiceCredential);
      final String clusterName = updateServiceCredential.get(0);
      final String uid = updateServiceCredential.get(1);
      final String serviceKeyFile = updateServiceCredential.get(2);

      account.removeCredential(clusterName, uid);
      final DCOSAccount.ClusterCredential credential =
          new DCOSAccount.ClusterCredential(clusterName, uid, null, serviceKeyFile);
      account.getClusters().add(credential);
    }

    try {
      List<String> oldRegistries =
          account.getDockerRegistries().stream()
              .map(DockerRegistryReference::getAccountName)
              .collect(Collectors.toList());

      List<DockerRegistryReference> newRegistries =
          updateStringList(oldRegistries, dockerRegistries, addDockerRegistry, removeDockerRegistry)
              .stream()
              .map(s -> new DockerRegistryReference().setAccountName(s))
              .collect(Collectors.toList());

      account.setDockerRegistries(newRegistries);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --docker-registries or --[add/remove]-docker-registry");
    }
    return account;
  }

  private void validateCredential(List<String> credential) {
    if (credential.size() < 2) {
      throw new IllegalArgumentException("Credential must have at least a cluster name and uid");
    }

    if (credential.size() > 3) {
      throw new IllegalArgumentException(
          "Credential may have at most 3 parts: cluster name, uid, and password/service account key");
    }
  }
}
