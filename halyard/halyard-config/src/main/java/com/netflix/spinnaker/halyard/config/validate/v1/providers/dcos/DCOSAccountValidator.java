package com.netflix.spinnaker.halyard.config.validate.v1.providers.dcos;

import static com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry.DockerRegistryReferenceValidation.validateDockerRegistries;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.WARNING;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** TODO: use clouddriver components for full validation (e.g. account name)) */
@Component
public class DCOSAccountValidator extends Validator<DCOSAccount> {
  @Override
  public void validate(final ConfigProblemSetBuilder problems, final DCOSAccount account) {
    DeploymentConfiguration deploymentConfiguration;

    /**
     * I have copied the code that was in the KubernetesAccountValidator
     *
     * <p>and which you were planning to refactor with filters
     *
     * <p>Forgive me It did the job And I was lazy so very lazy
     */
    // TODO(lwander) this is still a little messy - I should use the filters to get the necessary
    // docker account
    Node parent = account.getParent();
    while (!(parent instanceof DeploymentConfiguration)) {
      // Note this will crash in the above check if the halconfig representation is corrupted
      // (that's ok, because it indicates a more serious error than we want to validate).
      parent = parent.getParent();
    }
    deploymentConfiguration = (DeploymentConfiguration) parent;
    validateClusters(problems, account);
    if (account.getClusters().isEmpty()) {
      problems
          .addProblem(ERROR, "Account does not have any clusters configured")
          .setRemediation(
              "Edit the account with either --update-user-credential or --update-service-credential");
    }

    final List<String> dockerRegistryNames =
        account.getDockerRegistries().stream()
            .map(DockerRegistryReference::getAccountName)
            .collect(Collectors.toList());
    validateDockerRegistries(
        problems, deploymentConfiguration, dockerRegistryNames, Provider.ProviderType.DCOS);
  }

  private void validateClusters(final ConfigProblemSetBuilder problems, final DCOSAccount account) {
    final NodeIterator children = account.getParent().getChildren();

    Node n = children.getNext();
    Set<String> definedClusters = new HashSet<>();
    while (n != null) {
      if (n instanceof DCOSCluster) {
        definedClusters.add(((DCOSCluster) n).getName());
      }
      n = children.getNext();
    }

    final Set<String> accountClusters =
        account.getClusters().stream().map(c -> c.getName()).collect(Collectors.toSet());
    accountClusters.removeAll(definedClusters);
    accountClusters.forEach(
        c ->
            problems
                .addProblem(ERROR, "Cluster \"" + c.toString() + "\" not defined for provider")
                .setRemediation("Add cluster to the provider or remove from the account")
                .setOptions(new ArrayList<>(definedClusters)));

    Set<List<String>> credentials = new HashSet<>();
    account
        .getClusters()
        .forEach(
            c -> {
              final List<String> key = Arrays.asList(c.getName(), c.getUid());
              if (credentials.contains(key)) {
                problems
                    .addProblem(
                        ERROR,
                        "Account contains duplicate credentials for cluster \""
                            + c.getName()
                            + "\" and user id \""
                            + c.getUid()
                            + "\".")
                    .setRemediation("Remove the duplicate credentials");
              } else {
                credentials.add(key);
              }

              // TODO(willgorman) once we have the clouddriver-dcos module pulled in we can just
              // validate whether or not
              // we can connect without a password
              if (StringUtils.isEmpty(c.getPassword())
                  && StringUtils.isEmpty(c.getServiceKeyFile())) {
                problems
                    .addProblem(
                        WARNING,
                        "Account has no password or service key.  Unless the cluster has security disabled this may be an error")
                    .setRemediation("Add a password or service key.");
              }

              if (!StringUtils.isEmpty(c.getPassword())
                  && !StringUtils.isEmpty(c.getServiceKeyFile())) {
                problems
                    .addProblem(ERROR, "Account has both a password and service key")
                    .setRemediation("Remove either the password or service key.");
              }

              if (StringUtils.isNotEmpty(c.getServiceKeyFile())) {
                String resolvedServiceKey = validatingFileDecrypt(problems, c.getServiceKeyFile());
                if (resolvedServiceKey == null) {
                  return;
                }

                if (StringUtils.isEmpty(resolvedServiceKey)) {
                  problems
                      .addProblem(
                          ERROR, "The supplied service key file does not exist or is empty.")
                      .setRemediation("Supply a valid service key file.");
                }
              }
            });
  }
}
