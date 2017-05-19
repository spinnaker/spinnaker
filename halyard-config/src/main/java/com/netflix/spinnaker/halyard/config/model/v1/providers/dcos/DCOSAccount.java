package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class DCOSAccount extends Account {
  private List<DockerRegistryReference> dockerRegistries = new ArrayList<>();
  private List<ClusterCredential> clusters;

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  public void removeCredential(String name, String uid) {
    clusters.removeIf(c -> c.getName().equals(name) && c.getUid().equals(uid));
  }

  protected List<String> dockerRegistriesOptions(ConfigProblemSetBuilder psBuilder) {
    DeploymentConfiguration context = parentOfType(DeploymentConfiguration.class);
    DockerRegistryProvider dockerRegistryProvider = context.getProviders().getDockerRegistry();

    if (dockerRegistryProvider != null) {
      return dockerRegistryProvider
          .getAccounts()
          .stream()
          .map(Account::getName)
          .collect(Collectors.toList());
    } else {
      return null;
    }
  }

  @Data
  public static class ClusterCredential {
    private final String name;
    private final String uid;
    private final String password;
    private final String serviceKey;
  }
}

