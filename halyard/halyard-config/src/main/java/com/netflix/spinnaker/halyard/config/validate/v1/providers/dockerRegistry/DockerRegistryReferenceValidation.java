package com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Helper for other providers that need to validate their own references to Docker registries */
public class DockerRegistryReferenceValidation {
  public static void validateDockerRegistries(
      ConfigProblemSetBuilder psBuilder,
      DeploymentConfiguration deployment,
      List<String> dockerRegistryNames,
      final Provider.ProviderType providerType) {

    // TODO(lwander) document how to use hal to add registries and link to that here.
    if (dockerRegistryNames == null || dockerRegistryNames.isEmpty()) {
      psBuilder
          .addProblem(
              ERROR,
              "You have not specified any docker registries to deploy to.",
              "dockerRegistries")
          .setRemediation(
              "Add a docker registry that can be found in this deployment's dockerRegistries provider.");
    }

    DockerRegistryProvider dockerRegistryProvider = deployment.getProviders().getDockerRegistry();
    if (dockerRegistryProvider == null
        || dockerRegistryProvider.getAccounts() == null
        || dockerRegistryProvider.getAccounts().isEmpty()) {
      psBuilder
          .addProblem(
              ERROR,
              "The docker registry provider has not yet been configured for this deployment.",
              "dockerRegistries")
          .setRemediation(providerType + " needs a Docker Registry as an image source to run.");
    } else if (!dockerRegistryProvider.isEnabled()) {
      psBuilder.addProblem(ERROR, "The docker registry provider needs to be enabled.");
    } else {
      List<String> availableRegistries =
          dockerRegistryProvider.getAccounts().stream()
              .map(Account::getName)
              .collect(Collectors.toList());

      Set<String> names = new HashSet<>();

      for (String registryName : dockerRegistryNames) {
        if (names.contains(registryName)) {
          psBuilder.addProblem(FATAL, "Docker registry " + registryName + " has been added twice.");
        }
        names.add(registryName);

        if (!availableRegistries.contains(registryName)) {
          psBuilder
              .addProblem(
                  ERROR,
                  "The chosen registry \""
                      + registryName
                      + "\" has not been configured in your halconfig.",
                  "dockerRegistries")
              .setRemediation(
                  "Either add \""
                      + registryName
                      + "\" as a new Docker Registry account, or pick a different one.");
        }
      }
    }
  }
}
