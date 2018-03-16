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
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
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
      converter = LocalFileConverter.class,
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
      names = "--omit-namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_NAMESPACES_DESCRIPTION
  )
  public List<String> omitNamespaces = new ArrayList<>();

  @Parameter(
      names = "--kinds",
      variableArity = true,
      description = KubernetesCommandProperties.KINDS_DESCRIPTION
  )
  public List<String> kinds = new ArrayList<>();

  @Parameter(
      names = "--omit-kinds",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_KINDS_DESCRIPTION
  )
  public List<String> omitKinds = new ArrayList<>();

  @Parameter(
      names = "--docker-registries",
      variableArity = true,
      description = KubernetesCommandProperties.DOCKER_REGISTRIES_DESCRIPTION
  )
  public List<String> dockerRegistries = new ArrayList<>();
  
  @Parameter(
      names = "--oauth-service-account",
      hidden = true
  )
  public String oAuthServiceAccount;
  
  @Parameter(
      names = "--oauth-scopes",
      variableArity = true,
      hidden = true
  )
  public List<String> oAuthScopes = new ArrayList<>();

  @Parameter(
      names = "--naming-strategy",
      hidden = true
  )
  public String namingStrategy;

  @Parameter(
      names = "--configure-image-pull-secrets",
      arity = 1,
      description = KubernetesCommandProperties.CONFIGURE_IMAGE_PULL_SECRETS_DESCRIPTION
  )
  public Boolean configureImagePullSecrets = true;

  @Override
  protected Account buildAccount(String accountName) {
    KubernetesAccount account = (KubernetesAccount) new KubernetesAccount().setName(accountName);
    account.setContext(context);
    account.setKubeconfigFile(kubeconfigFile);
    account.setNamespaces(namespaces);
    account.setOmitNamespaces(omitNamespaces);
    account.setKinds(namespaces);
    account.setOmitKinds(omitKinds);
    account.setConfigureImagePullSecrets(configureImagePullSecrets);
    dockerRegistries.forEach(registryName -> account.getDockerRegistries().add(new DockerRegistryReference().setAccountName(registryName)));
    account.setOAuthServiceAccount(oAuthServiceAccount);
    account.setOAuthScopes(oAuthScopes);
    account.setNamingStrategy(namingStrategy);
    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new KubernetesAccount();
  }
}
