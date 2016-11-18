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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblem;
import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.Halconfig;
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

  public List<HalconfigProblem> validateKubeconfig(DeploymentConfiguration deployment, HalconfigCoordinates coordinates) {
    ArrayList<HalconfigProblem> result = new ArrayList<>();
    io.fabric8.kubernetes.api.model.Config kubeconfig;

    // This indicates if a first pass at the config looks OK. If we don't see any serious problems, we'll do one last check
    // against the requested kubernetes cluster to ensure that we can run spinnaker.
    boolean smoketest = true;

    try {
      File kubeconfigFileOpen = new File(getKubeconfigFile());
      kubeconfig = KubeConfigUtils.parseConfig(kubeconfigFileOpen);
    } catch (IOException e) {
      result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates, e.getMessage()));
      return result;
    }

    if (context != null && !context.isEmpty()) {
      Optional<NamedContext> namedContext = kubeconfig
          .getContexts()
          .stream()
          .filter(c -> c.getName().equals(context))
          .findFirst();

      if (!namedContext.isPresent()) {
        result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
            "Context \"" + context + "\" not found in kubeconfig \"" + getKubeconfigFile() + "\"",
            "Either add this context to your kubeconfig, rely on the default context, or pick another kubeconfig file"));
        smoketest = false;
      }
    } else {
      String currentContext = kubeconfig.getCurrentContext();
      if (currentContext.isEmpty()) {
        result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
            "You have not specified a Kubernetes context, and your kubeconfig \"" + getKubeconfigFile() + "\" has no current-context",
            "Either specify a context in your halconfig, or set a current-context in your kubeconfig"));
        smoketest = false;
      } else {
        result.add(new HalconfigProblem(HalconfigProblem.Severity.WARNING, coordinates,
            "You have not specified a Kubernetes context in your halconfig, Spinnaker will use \"" + currentContext + "\" instead",
            "We recommend explicitly setting a context in your halconfig, to ensure changes to your kubeconfig won't break your deployment"));
      }
    }

    if (smoketest) {
      Config config = KubernetesConfigParser.parse(getKubeconfigFile(), context, cluster, user, namespaces);
      KubernetesClient client = new DefaultKubernetesClient(config);

      try {
        client.namespaces().list();
      } catch (Exception e) {
        result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
            "Unable to communicate with your Kubernetes cluster: " + e.getMessage(),
            "Verify that these credentials work manually using \"kubectl\""));
      }
    }

    return result;
  }

  public List<HalconfigProblem> validateDockerRegistries(DeploymentConfiguration deployment, HalconfigCoordinates coordinates) {
    ArrayList<HalconfigProblem> result = new ArrayList<>();

    if (dockerRegistries == null || dockerRegistries.isEmpty()) {
      result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
          "You have not specified any docker registries to deploy to",
          "Add a docker registry that can be found in this deployment's dockerRegistries provider"));
    }

    DockerRegistryProvider dockerRegistryProvider = deployment.getProviders().getDockerRegistry();
    if (dockerRegistryProvider == null || dockerRegistryProvider.getAccounts() == null || dockerRegistryProvider.getAccounts().isEmpty()) {
      result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
          "The docker registry provider has not yet been configured for this deployment",
          "Kubernetes needs a Docker Registry as an image source to run"));
    } else {
      List<String> availableRegistries = dockerRegistryProvider
          .getAccounts()
          .stream()
          .map(Account::getName).collect(Collectors.toList());

      for (DockerRegistryReference registryReference : dockerRegistries) {
        if (!availableRegistries.contains(registryReference.accountName)) {
          result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
              "The chosen registry \"" + registryReference.accountName + "\" has not been configured in your halconfig",
              "Either add \"" + registryReference.accountName + "\" as a new Docker Registry account, or pick a different one"));
        }

        if (!registryReference.namespaces.isEmpty() && !namespaces.isEmpty()) {
          for (String namespace : registryReference.namespaces) {
            if (!namespaces.contains(namespace)) {
              result.add(new HalconfigProblem(HalconfigProblem.Severity.ERROR, coordinates,
                  "The deployable namespace \"" + namespace + "\" for registry \"" + registryReference.accountName + "\" is not accessibly by this kubernetes account",
                  "Either remove this namespace from this docker registry, add the namespace to the account's list of namespaces, or drop the list of namespaces"));
            }
          }
        }
      }
    }

    return result;
  }

  public List<HalconfigProblem> validate(DeploymentConfiguration deployment, HalconfigCoordinates coordinates) {
    List<HalconfigProblem> result = validateDockerRegistries(deployment, coordinates);
    result.addAll(validateKubeconfig(deployment, coordinates));

    return result;
  }
}
