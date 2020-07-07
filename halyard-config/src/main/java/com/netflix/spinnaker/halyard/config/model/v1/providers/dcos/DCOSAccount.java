package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.ContainerAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"providerVersion"})
public class DCOSAccount extends ContainerAccount {
  private List<ClusterCredential> clusters;

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        clusters.stream().map(c -> (Node) c).collect(Collectors.toList()));
  }

  public void removeCredential(String name, String uid) {
    clusters.removeIf(c -> c.getName().equals(name) && c.getUid().equals(uid));
  }

  protected List<String> dockerRegistriesOptions(ConfigProblemSetBuilder psBuilder) {
    DeploymentConfiguration context = parentOfType(DeploymentConfiguration.class);
    DockerRegistryProvider dockerRegistryProvider = context.getProviders().getDockerRegistry();

    if (dockerRegistryProvider != null) {
      return dockerRegistryProvider.getAccounts().stream()
          .map(Account::getName)
          .collect(Collectors.toList());
    } else {
      return null;
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class ClusterCredential extends Node implements Cloneable {
    private final String name;
    private final String uid;
    @Secret private final String password;
    @LocalFile @SecretFile private final String serviceKeyFile;

    @Override
    public String getNodeName() {
      return name;
    }

    @Override
    public NodeIterator getChildren() {
      return NodeIteratorFactory.makeEmptyIterator();
    }
  }
}
