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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount.ProviderVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Parameters(separators = "=")
public class KubernetesEditAccountCommand extends AbstractEditAccountCommand<KubernetesAccount> {
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
      names = "--clear-context",
      description =
          "Removes the currently configured context, defaulting to 'current-context' in your kubeconfig."
              + "See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information.")
  private boolean clearContext;

  @Parameter(
      names = "--namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.NAMESPACES_DESCRIPTION)
  private List<String> namespaces = new ArrayList<>();

  @Parameter(
      names = "--all-namespaces",
      description =
          "Set the list of namespaces to cache and deploy to every namespace available to your supplied credentials.")
  private boolean allNamespaces;

  @Parameter(
      names = "--add-namespace",
      description = "Add this namespace to the list of namespaces to manage.")
  private String addNamespace;

  @Parameter(
      names = "--remove-namespace",
      description = "Remove this namespace to the list of namespaces to manage.")
  private String removeNamespace;

  @Parameter(
      names = "--omit-namespaces",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_NAMESPACES_DESCRIPTION)
  private List<String> omitNamespaces = new ArrayList<>();

  @Parameter(
      names = "--add-omit-namespace",
      description = "Add this namespace to the list of namespaces to omit.")
  private String addOmitNamespace;

  @Parameter(
      names = "--remove-omit-namespace",
      description = "Remove this namespace to the list of namespaces to omit.")
  private String removeOmitNamespace;

  @Parameter(
      names = "--kinds",
      variableArity = true,
      description = KubernetesCommandProperties.KINDS_DESCRIPTION)
  private List<String> kinds = new ArrayList<>();

  @Parameter(
      names = "--all-kinds",
      description =
          "Set the list of kinds to cache and deploy to every kind available to your supplied credentials.")
  private boolean allKinds;

  @Parameter(names = "--add-kind", description = "Add this kind to the list of kinds to manage.")
  private String addKind;

  @Parameter(
      names = "--remove-kind",
      description = "Remove this kind to the list of kinds to manage.")
  private String removeKind;

  @Parameter(
      names = "--omit-kinds",
      variableArity = true,
      description = KubernetesCommandProperties.OMIT_KINDS_DESCRIPTION)
  private List<String> omitKinds = new ArrayList<>();

  @Parameter(names = "--add-omit-kind", description = "Add this kind to the list of kinds to omit.")
  private String addOmitKind;

  @Parameter(
      names = "--remove-omit-kind",
      description = "Remove this kind to the list of kinds to omit.")
  private String removeOmitKind;

  @Parameter(
      names = "--docker-registries",
      variableArity = true,
      description = KubernetesCommandProperties.DOCKER_REGISTRIES_DESCRIPTION)
  public List<String> dockerRegistries = new ArrayList<>();

  @Parameter(
      names = "--add-docker-registry",
      description =
          "Add this docker registry to the list of docker registries to use as a source of images.")
  private String addDockerRegistry;

  @Parameter(
      names = "--remove-docker-registry",
      description =
          "Remove this docker registry from the list of docker registries to use as a source of images.")
  private String removeDockerRegistry;

  @Parameter(names = "--oauth-service-account", hidden = true)
  public String oAuthServiceAccount;

  @Parameter(names = "--oauth-scopes", variableArity = true, hidden = true)
  public List<String> oAuthScopes = new ArrayList<>();

  @Parameter(names = "--add-oauth-scope", hidden = true)
  public String addOAuthScope;

  @Parameter(names = "--remove-oauth-scope", hidden = true)
  public String removeOAuthScope;

  @Parameter(names = "--naming-strategy", hidden = true)
  public String namingStrategy;

  @Parameter(
      names = "--add-custom-resource",
      arity = 1,
      description = KubernetesCommandProperties.CUSTOM_RESOURCES)
  public String addCustomResourceName;

  @Parameter(
      names = "--spinnaker-kind",
      arity = 1,
      description = "Set the Spinnaker kind for custom resource being added.")
  public String addCustomResourceSpinnakerKind;

  @Parameter(
      names = "--versioned",
      arity = 1,
      description = "Configure whether the custom resource being added is versioned by Spinnaker.")
  public Boolean addCustomResourceVersioned;

  @Parameter(
      names = "--remove-custom-resource",
      arity = 1,
      description =
          "Remove this Kubernetes custom resource by name from the list of custom resources to manage.")
  public String removeCustomResource;

  @Parameter(
      names = "--service-account",
      arity = 1,
      description = KubernetesCommandProperties.SERVICE_ACCOUNT_DESCRIPTION)
  public Boolean serviceAccount;

  @Parameter(
      names = "--configure-image-pull-secrets",
      arity = 1,
      description = KubernetesCommandProperties.CONFIGURE_IMAGE_PULL_SECRETS_DESCRIPTION)
  public Boolean configureImagePullSecrets;

  @Parameter(names = "--skin", arity = 1, hidden = true)
  public String skin;

  @Parameter(
      names = "--only-spinnaker-managed",
      arity = 1,
      description = KubernetesCommandProperties.ONLY_SPINNAKER_MANAGED_DESCRIPTION)
  public Boolean onlySpinnakerManaged;

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
      names = "--raw-resource-endpoint-kind-expressions",
      description = KubernetesCommandProperties.RAW_RESOURCES_ENDPOINT_KIND_EXPRESSIONS)
  private List<String> rreKindExpressions = new ArrayList<>();

  @Parameter(
      names = "--add-raw-resource-endpoint-kind-expression",
      description =
          "Add this expression to the list of kind expressions for the raw resource endpoint configuration.")
  private String addRREKindExpression;

  @Parameter(
      names = "--remove-raw-resource-endpoint-kind-expression",
      description =
          "Remove this expression from list of kind expressions for the raw resource endpoint configuration.")
  private String removeRREKindExpression;

  @Parameter(
      names = "--raw-resource-endpoint-omit-kind-expressions",
      variableArity = true,
      description = KubernetesCommandProperties.RAW_RESOURCES_ENDPOINT_OMIT_KIND_EXPRESSIONS)
  private List<String> rreOmitKindExpressions = new ArrayList<>();

  @Parameter(
      names = "--add-raw-resource-endpoint-omit-kind-expression",
      description =
          "Add this expression to the list of omit kind expressions for the raw resources endpoint configuration.")
  private String addRREOmitKindExpression;

  @Parameter(
      names = "--remove-raw-resource-endpoint-omit-kind-expression",
      description =
          "Remove this expression from the list of omit kind expressions for the raw resources endpoint configuration.")
  private String removeRREOmitKindExpression;

  @Parameter(
      names = "--cache-threads",
      arity = 1,
      description = KubernetesCommandProperties.CACHE_THREADS)
  private Integer cacheThreads;

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
      names = "--provider-version",
      description = KubernetesCommandProperties.PROVIDER_VERSION_DESCRIPTION)
  private ProviderVersion providerVersion;

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

    account.setKubeconfigFile(isSet(kubeconfigFile) ? kubeconfigFile : account.getKubeconfigFile());
    account.setConfigureImagePullSecrets(
        isSet(configureImagePullSecrets)
            ? configureImagePullSecrets
            : account.getConfigureImagePullSecrets());
    account.setServiceAccount(isSet(serviceAccount) ? serviceAccount : account.getServiceAccount());

    if (allNamespaces) {
      account.setNamespaces(new ArrayList<>());
    } else {
      try {
        account.setNamespaces(
            updateStringList(account.getNamespaces(), namespaces, addNamespace, removeNamespace));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Set either --namespaces or --[add/remove]-namespace");
      }
    }

    try {
      account.setOmitNamespaces(
          updateStringList(
              account.getOmitNamespaces(), omitNamespaces, addOmitNamespace, removeOmitNamespace));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --omit-namespaces or --[add/remove]-omit-namespace");
    }

    try {
      account.setKinds(updateStringList(account.getKinds(), kinds, addKind, removeKind));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --kinds or --[add/remove]-kind");
    }

    try {
      account.setOmitKinds(
          updateStringList(account.getOmitKinds(), omitKinds, addOmitKind, removeOmitKind));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --omit-kinds or --[add/remove]-omit-kind");
    }

    if (isSet(addCustomResourceName)) {
      KubernetesAccount.CustomKubernetesResource customKubernetesResource =
          new KubernetesAccount.CustomKubernetesResource();
      customKubernetesResource.setKubernetesKind(addCustomResourceName);
      customKubernetesResource.setSpinnakerKind(
          isSet(addCustomResourceSpinnakerKind)
              ? addCustomResourceSpinnakerKind
              : customKubernetesResource.getSpinnakerKind());
      customKubernetesResource.setVersioned(
          isSet(addCustomResourceVersioned)
              ? addCustomResourceVersioned
              : customKubernetesResource.isVersioned());
      account.getCustomResources().add(customKubernetesResource);
    } else {
      if (isSet(addCustomResourceSpinnakerKind) || isSet(addCustomResourceVersioned)) {
        throw new IllegalArgumentException(
            "\"--spinnaker-kind\" and \"--versioned\" can only be used with \"--add-custom-resource\" "
                + "to set the name for the custom resource.");
      }
    }

    if (isSet(removeCustomResource)) {
      List<KubernetesAccount.CustomKubernetesResource> newCustomResources =
          account.getCustomResources().stream()
              .filter(entry -> !entry.getKubernetesKind().equals(removeCustomResource))
              .collect(Collectors.toList());

      account.setCustomResources(newCustomResources);
    }

    try {
      List<String> oldRegistries =
          account.getDockerRegistries().stream()
              .map(DockerRegistryReference::getAccountName)
              .collect(Collectors.toList());

      List<DockerRegistryReference> newRegistries =
          updateStringList(oldRegistries, dockerRegistries, addDockerRegistry, removeDockerRegistry)
              .stream()
              .map(s -> new DockerRegistryReference().setAccountName(s))
              .collect(Collectors.toList());

      account.setDockerRegistries(newRegistries);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --docker-registries or --[add/remove]-docker-registry");
    }

    try {
      account.setOAuthScopes(
          updateStringList(account.getOAuthScopes(), oAuthScopes, addOAuthScope, removeOAuthScope));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --oauth-scopes or --[add/remove]-oauth-scope");
    }

    account.setOAuthServiceAccount(
        isSet(oAuthServiceAccount) ? oAuthServiceAccount : account.getOAuthServiceAccount());
    account.setNamingStrategy(isSet(namingStrategy) ? namingStrategy : account.getNamingStrategy());
    account.setSkin(isSet(skin) ? skin : account.getSkin());
    account.setOnlySpinnakerManaged(
        isSet(onlySpinnakerManaged) ? onlySpinnakerManaged : account.getOnlySpinnakerManaged());
    account.setCheckPermissionsOnStartup(
        isSet(checkPermissionsOnStartup)
            ? checkPermissionsOnStartup
            : account.getCheckPermissionsOnStartup());
    account.setLiveManifestCalls(
        isSet(liveManifestCalls) ? liveManifestCalls : account.getLiveManifestCalls());
    account.setCacheThreads(isSet(cacheThreads) ? cacheThreads : account.getCacheThreads());
    account.setCacheIntervalSeconds(
        isSet(cacheIntervalSeconds) ? cacheIntervalSeconds : account.getCacheIntervalSeconds());
    account.setCacheAllApplicationRelationships(
        isSet(cacheAllApplicationRelationships)
            ? cacheAllApplicationRelationships
            : account.getCacheAllApplicationRelationships());

    try {
      account
          .getRawResourcesEndpointConfig()
          .setKindExpressions(
              updateStringList(
                  account.getRawResourcesEndpointConfig().getKindExpressions(),
                  rreKindExpressions,
                  addRREKindExpression,
                  removeRREKindExpression));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --raw-resource-endpoint-kind-expressions or --[add/remove]-raw-resource-kind-expression");
    }

    try {
      account
          .getRawResourcesEndpointConfig()
          .setOmitKindExpressions(
              updateStringList(
                  account.getRawResourcesEndpointConfig().getOmitKindExpressions(),
                  rreOmitKindExpressions,
                  addRREOmitKindExpression,
                  removeRREOmitKindExpression));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --raw-resource-endpoint-omit-kind-expressions or --[add/remove]-raw-resource-endpoint-omit-kind-expression");
    }

    if (isSet(providerVersion)) {
      account.setProviderVersion(providerVersion);
    }

    return account;
  }
}
