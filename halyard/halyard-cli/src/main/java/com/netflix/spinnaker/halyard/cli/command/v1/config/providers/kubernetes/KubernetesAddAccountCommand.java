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
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount.ProviderVersion;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class KubernetesAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "kubernetes";
  }

  @Parameter(names = "--context", description = KubernetesCommandProperties.CONTEXT_DESCRIPTION)
  private String context;

  @Parameter(
      names = "--kubeconfig-file",
      converter = LocalFileConverter.class,
      description = KubernetesCommandProperties.KUBECONFIG_DESCRIPTION)
  private String kubeconfigFile;

  @Parameter(
      names = "--namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.NAMESPACES_DESCRIPTION)
  public List<String> namespaces = new ArrayList<>();

  @Parameter(
      names = "--omit-namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_NAMESPACES_DESCRIPTION)
  public List<String> omitNamespaces = new ArrayList<>();

  @Parameter(
      names = "--kinds",
      variableArity = true,
      description = KubernetesCommandProperties.KINDS_DESCRIPTION)
  public List<String> kinds = new ArrayList<>();

  @Parameter(
      names = "--omit-kinds",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_KINDS_DESCRIPTION)
  public List<String> omitKinds = new ArrayList<>();

  @Parameter(
      names = "--docker-registries",
      variableArity = true,
      description = KubernetesCommandProperties.DOCKER_REGISTRIES_DESCRIPTION)
  public List<String> dockerRegistries = new ArrayList<>();

  @Parameter(names = "--oauth-service-account", hidden = true)
  public String oAuthServiceAccount;

  @Parameter(names = "--oauth-scopes", variableArity = true, hidden = true)
  public List<String> oAuthScopes = new ArrayList<>();

  @Parameter(names = "--naming-strategy", hidden = true)
  public String namingStrategy;

  @Parameter(
      names = "--service-account",
      arity = 1,
      description = KubernetesCommandProperties.SERVICE_ACCOUNT_DESCRIPTION)
  public Boolean serviceAccount;

  @Parameter(
      names = "--configure-image-pull-secrets",
      arity = 1,
      description = KubernetesCommandProperties.CONFIGURE_IMAGE_PULL_SECRETS_DESCRIPTION)
  public Boolean configureImagePullSecrets = true;

  @Parameter(names = "--skin", arity = 1, hidden = true)
  public String skin;

  @Parameter(
      names = "--only-spinnaker-managed",
      arity = 1,
      description = KubernetesCommandProperties.ONLY_SPINNAKER_MANAGED_DESCRIPTION)
  public Boolean onlySpinnakerManaged = false;

  @Parameter(
      names = "--check-permissions-on-startup",
      arity = 1,
      description = KubernetesCommandProperties.CHECK_PERMISSIONS_ON_STARTUP)
  public Boolean checkPermissionsOnStartup;

  @Parameter(
      names = "--live-manifest-calls",
      arity = 1,
      description = KubernetesCommandProperties.LIVE_MANIFEST_CALLS)
  public Boolean liveManifestCalls;

  @Parameter(
      names = "--cache-threads",
      arity = 1,
      description = KubernetesCommandProperties.CACHE_THREADS)
  private int cacheThreads = 1;

  @Parameter(
      names = "--cache-interval-seconds",
      arity = 1,
      description = KubernetesCommandProperties.CACHE_INTERVAL_SECONDS_DESCRIPTION)
  private Long cacheIntervalSeconds;

  @Parameter(
      names = "--cache-all-application-relationships",
      arity = 1,
      description = KubernetesCommandProperties.CACHE_ALL_APPLICATION_RELATIONSHIPS)
  public Boolean cacheAllApplicationRelationships;

  @Parameter(
      names = "--raw-resource-endpoint-kind-expressions",
      variableArity = true,
      description = KubernetesCommandProperties.RAW_RESOURCES_ENDPOINT_KIND_EXPRESSIONS)
  public List<String> rawResourcekindExpressions = new ArrayList<>();

  @Parameter(
      names = "--raw-resource-endpoint-omit-kind-expressions",
      variableArity = true,
      description = KubernetesCommandProperties.RAW_RESOURCES_ENDPOINT_OMIT_KIND_EXPRESSIONS)
  public List<String> rawResourceOmitKindExpressions = new ArrayList<>();

  @Parameter(
      names = "--provider-version",
      description = KubernetesCommandProperties.PROVIDER_VERSION_DESCRIPTION)
  private ProviderVersion providerVersion = ProviderVersion.V2;

  @Override
  protected Account buildAccount(String accountName) {
    KubernetesAccount account = (KubernetesAccount) new KubernetesAccount().setName(accountName);
    account.setContext(context);
    account.setKubeconfigFile(kubeconfigFile);
    account.setNamespaces(namespaces);
    account.setOmitNamespaces(omitNamespaces);
    account.setKinds(kinds);
    account.setOmitKinds(omitKinds);
    account.setConfigureImagePullSecrets(configureImagePullSecrets);
    account.setServiceAccount(serviceAccount);
    dockerRegistries.forEach(
        registryName ->
            account
                .getDockerRegistries()
                .add(new DockerRegistryReference().setAccountName(registryName)));
    account.setOAuthServiceAccount(oAuthServiceAccount);
    account.setOAuthScopes(oAuthScopes);
    account.setNamingStrategy(namingStrategy);
    account.setSkin(skin);
    account.setOnlySpinnakerManaged(onlySpinnakerManaged);
    account.setCheckPermissionsOnStartup(checkPermissionsOnStartup);
    account.setLiveManifestCalls(liveManifestCalls);
    account.setCacheThreads(cacheThreads);
    account.setCacheIntervalSeconds(cacheIntervalSeconds);
    account.setCacheAllApplicationRelationships(cacheAllApplicationRelationships);
    account.getRawResourcesEndpointConfig().setKindExpressions(rawResourcekindExpressions);
    account.getRawResourcesEndpointConfig().setOmitKindExpressions(rawResourceOmitKindExpressions);
    account.setProviderVersion(providerVersion);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new KubernetesAccount();
  }
}
