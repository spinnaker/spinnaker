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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.PathExpandingConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters()
public class KubernetesAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "kubernetes";
  }

  @Parameter(
      names = "--context",
      description = KubernetesCommandProperties.CONTEXT_DESCRIPTION
  )
  private String context;

  @Parameter(
      names = "--kubeconfig-file",
      converter = PathExpandingConverter.class,
      description = KubernetesCommandProperties.KUBECONFIG_DESCRIPTION
  )
  private String kubeconfigFile;

  @Parameter(
      names = "--namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.NAMESPACES_DESCRIPTION
  )
  public List<String> namespaces = new ArrayList<>();

  @Parameter(
      names = "--docker-registries",
      required = true,
      variableArity = true,
      description = KubernetesCommandProperties.DOCKER_REGISTRIES_DESCRIPTION
  )
  public List<String> dockerRegistries = new ArrayList<>();

  @Override
  protected Account buildAccount(String accountName) {
    KubernetesAccount account = (KubernetesAccount) new KubernetesAccount().setName(accountName);
    account.setContext(context);
    account.setKubeconfigFile(kubeconfigFile);
    account.setNamespaces(namespaces);
    dockerRegistries.forEach(registryName -> account.getDockerRegistries().add(new DockerRegistryReference().setAccountName(registryName)));
    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new KubernetesAccount();
  }
}
