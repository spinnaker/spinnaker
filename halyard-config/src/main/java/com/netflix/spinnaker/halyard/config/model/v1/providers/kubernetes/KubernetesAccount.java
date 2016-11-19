/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity.ERROR;
import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity.WARNING;

@Data
@EqualsAndHashCode(callSuper = false)
public class KubernetesAccount extends Account implements Cloneable {
  String context;
  String cluster;
  String user;
  String kubeconfigFile;
  List<String> namespaces = new ArrayList<>();
  List<DockerRegistryReference> dockerRegistries = new ArrayList<>();

  public String getKubeconfigFile() {
    if (kubeconfigFile == null || kubeconfigFile.isEmpty()) {
      return System.getProperty("user.home") + "/.kube/config";
    } else {
      return kubeconfigFile;
    }
  }

  private void validateKubeconfig(ProblemSetBuilder builder, DeploymentConfiguration deployment) {
    io.fabric8.kubernetes.api.model.Config kubeconfig;

    // This indicates if a first pass at the config looks OK. If we don't see any serious problems, we'll do one last check
    // against the requested kubernetes cluster to ensure that we can run spinnaker.
    boolean smoketest = true;

    try {
      File kubeconfigFileOpen = new File(getKubeconfigFile());
      kubeconfig = KubeConfigUtils.parseConfig(kubeconfigFileOpen);
    } catch (IOException e) {
      builder.addProblem(ERROR, e.getMessage());
      return;
    }

    if (context != null && !context.isEmpty()) {
      Optional<NamedContext> namedContext = kubeconfig
          .getContexts()
          .stream()
          .filter(c -> c.getName().equals(context))
          .findFirst();

      if (!namedContext.isPresent()) {
        builder.addProblem(ERROR, "Context \"" + context + "\" not found in kubeconfig \"" + getKubeconfigFile() + "\"")
            .setRemediation("Either add this context to your kubeconfig, rely on the default context, or pick another kubeconfig file");
        smoketest = false;
      }
    } else {
      String currentContext = kubeconfig.getCurrentContext();
      if (currentContext.isEmpty()) {
        builder.addProblem(ERROR, "You have not specified a Kubernetes context, and your kubeconfig \"" + getKubeconfigFile() + "\" has no current-context")
            .setRemediation("Either specify a context in your halconfig, or set a current-context in your kubeconfig");
        smoketest = false;
      } else {
        builder.addProblem(WARNING, "You have not specified a Kubernetes context in your halconfig, Spinnaker will use \"" + currentContext + "\" instead")
            .setRemediation("We recommend explicitly setting a context in your halconfig, to ensure changes to your kubeconfig won't break your deployment");
      }
    }

    if (smoketest) {
      Config config = KubernetesConfigParser.parse(getKubeconfigFile(), context, cluster, user, namespaces);
      KubernetesClient client = new DefaultKubernetesClient(config);

      try {
        client.namespaces().list();
      } catch (Exception e) {
        builder.addProblem(ERROR, "Unable to communicate with your Kubernetes cluster: " + e.getMessage())
            .setRemediation("Verify that these credentials work manually using \"kubectl\"");
      }
    }
  }

  public void validateDockerRegistries(ProblemSetBuilder builder, DeploymentConfiguration deployment) {
    if (dockerRegistries == null || dockerRegistries.isEmpty()) {
      builder.addProblem(ERROR, "You have not specified any docker registries to deploy to")
          .setRemediation("Add a docker registry that can be found in this deployment's dockerRegistries provider");
    }

    DockerRegistryProvider dockerRegistryProvider = deployment.getProviders().getDockerRegistry();
    if (dockerRegistryProvider == null || dockerRegistryProvider.getAccounts() == null || dockerRegistryProvider.getAccounts().isEmpty()) {
      builder.addProblem(ERROR, "The docker registry provider has not yet been configured for this deployment")
          .setRemediation("Kubernetes needs a Docker Registry as an image source to run");
    } else {
      List<String> availableRegistries = dockerRegistryProvider
          .getAccounts()
          .stream()
          .map(Account::getName).collect(Collectors.toList());

      for (DockerRegistryReference registryReference : dockerRegistries) {
        if (!availableRegistries.contains(registryReference.accountName)) {
          builder.addProblem(ERROR, "The chosen registry \"" + registryReference.accountName + "\" has not been configured in your halconfig")
              .setRemediation("Either add \"" + registryReference.accountName + "\" as a new Docker Registry account, or pick a different one");
        }

        if (!registryReference.namespaces.isEmpty() && !namespaces.isEmpty()) {
          for (String namespace : registryReference.namespaces) {
            if (!namespaces.contains(namespace)) {
              builder.addProblem(ERROR, "The deployable namespace \"" + namespace + "\" for registry \"" + registryReference.accountName + "\" is not accessibly by this kubernetes account")
                  .setRemediation("Either remove this namespace from this docker registry, add the namespace to the account's list of namespaces, or drop the list of namespaces");
            }
          }
        }
      }
    }
  }

  public void accept(Validator v) {
    v.validate(this);
  }

  public void validate(ProblemSetBuilder builder, DeploymentConfiguration deployment) {
    validateDockerRegistries(builder, deployment);
    validateKubeconfig(builder, deployment);
  }

  @Override
  public NodeIterator getIterator() {
    return NodeIteratorFactory.getEmptyIterator();
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.ACCOUNT;
  }
}
