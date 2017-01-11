/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.kubernetes;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesEditAccountCommand extends AbstractEditAccountCommand<KubernetesAccount> {
  protected String getProviderName() {
    return "kubernetes";
  }

  @Parameter(
      names = "--context",
      description = KubernetesCommandProperties.CONTEXT_DESCRIPTION
  )
  private String context;

  @Parameter(
      names = "--clear-context",
      description = "Removes the currently configured context, defaulting to 'current-context' in your kubeconfig."
          + "See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information."
  )
  private boolean clearContext;

  @Parameter(
      names = "--namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.NAMESPACES_DESCRIPTION
  )
  private List<String> namespaces = new ArrayList<>();

  @Parameter(
      names = "--all-namespaces",
      description = "Set the list of namespaces to cache and deploy to every namespace available to your supplied credentials."
  )
  private boolean allNamespaces;

  @Parameter(
      names = "--add-namespace",
      description = "Add this namespace to the list of namespaces to manage."
  )
  private String addNamespace;

  @Parameter(
      names = "--remove-namespace",
      description = "Remove this namespace to the list of namespaces to manage."
  )
  private String removeNamespace;

  @Parameter(
      names = "--docker-registries",
      variableArity = true,
      description = KubernetesCommandProperties.DOCKER_REGISTRIES_DESCRIPTION
  )
  public List<String> dockerRegistries = new ArrayList<>();

  @Parameter(
      names = "--add-docker-registry",
      description = "Add this docker registry to the list of docker registries to use as a source of images."
  )
  private String addDockerRegistry;

  @Parameter(
      names = "--remove-docker-registry",
      description = "Remove this docker registry from the list of docker registries to use as a source of images."
  )
  private String removeDockerRegistry;

  @Override
  protected Account editAccount(KubernetesAccount account) {
    boolean contextSet = context != null && !context.isEmpty();
    if (contextSet && !clearContext) {
      account.setContext(context);
    } else if (!contextSet && clearContext) {
      account.setContext(null);
    } else if (contextSet && clearContext) {
      throw new IllegalArgumentException("Set either --context or --clear-context");
    }

    try {
      account.setNamespaces(
          updateStringList(account.getNamespaces(), namespaces, addNamespace, removeNamespace));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --namespace or --[add/remove]-namespace");
    }

    try {
      List<String> oldRegistries = account.getDockerRegistries()
          .stream()
          .map(DockerRegistryReference::getAccountName)
          .collect(Collectors.toList());

      List<DockerRegistryReference> newRegistries = updateStringList(oldRegistries, dockerRegistries, addDockerRegistry, removeDockerRegistry)
          .stream()
          .map(s -> new DockerRegistryReference().setAccountName(s))
          .collect(Collectors.toList());

      account.setDockerRegistries(newRegistries);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --docker-registries or --[add/remove]-docker-registry");
    }

    return account;
  }

}
