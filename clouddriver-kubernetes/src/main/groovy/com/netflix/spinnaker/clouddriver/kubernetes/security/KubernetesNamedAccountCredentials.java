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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import groovy.util.logging.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

import static com.netflix.spinnaker.clouddriver.security.ProviderVersion.v1;

@Slf4j
public class KubernetesNamedAccountCredentials<C extends KubernetesCredentials> implements AccountCredentials<C> {
  final private String cloudProvider = "kubernetes";
  final private String name;
  final private ProviderVersion version;
  final private String environment;
  final private String accountType;
  final private String context;
  final private String cluster;
  final private String user;
  final private String userAgent;
  final private String kubeconfigFile;
  final private Boolean serviceAccount;
  private List<String> namespaces;
  private List<String> omitNamespaces;
  final private int cacheThreads;
  private C credentials;
  private final List<String> requiredGroupMembership;
  private final Permissions permissions;
  private final List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final Registry spectatorRegistry;
  private final AccountCredentialsRepository accountCredentialsRepository;

  KubernetesNamedAccountCredentials(String name,
                                    ProviderVersion version,
                                    AccountCredentialsRepository accountCredentialsRepository,
                                    String userAgent,
                                    String environment,
                                    String accountType,
                                    String context,
                                    String cluster,
                                    String user,
                                    String kubeconfigFile,
                                    Boolean serviceAccount,
                                    List<String> namespaces,
                                    List<String> omitNamespaces,
                                    int cacheThreads,
                                    List<LinkedDockerRegistryConfiguration> dockerRegistries,
                                    List<String> requiredGroupMembership,
                                    Permissions permissions,
                                    Registry spectatorRegistry,
                                    C credentials) {
    this.name = name;
    this.version = version;
    this.environment = environment;
    this.accountType = accountType;
    this.context = context;
    this.cluster = cluster;
    this.user = user;
    this.userAgent = userAgent;
    this.kubeconfigFile = kubeconfigFile;
    this.serviceAccount = serviceAccount;
    this.namespaces = namespaces;
    this.omitNamespaces = omitNamespaces;
    this.cacheThreads = cacheThreads;
    this.requiredGroupMembership = requiredGroupMembership;
    this.permissions = permissions;
    this.dockerRegistries = dockerRegistries;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.spectatorRegistry = spectatorRegistry;
    this.credentials = credentials;
  }

  public List<String> getNamespaces() {
    return credentials.getDeclaredNamespaces();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ProviderVersion getVersion() {
    return version;
  }

  @Override
  public String getEnvironment() {
    return environment;
  }

  @Override
  public String getAccountType() {
    return accountType;
  }

  @Override
  public C getCredentials() {
    return credentials;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  public int getCacheThreads() {
    return cacheThreads;
  }

  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return dockerRegistries;
  }

  public Permissions getPermissions() {
    return permissions;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  static class Builder<C extends KubernetesCredentials> {
    String name;
    ProviderVersion version;
    String environment;
    String accountType;
    String context;
    String cluster;
    String user;
    String userAgent;
    String kubeconfigFile;
    Boolean serviceAccount;
    List<String> namespaces;
    List<String> omitNamespaces;
    int cacheThreads;
    C credentials;
    List<String> requiredGroupMembership;
    Permissions permissions;
    List<LinkedDockerRegistryConfiguration> dockerRegistries;
    Registry spectatorRegistry;
    AccountCredentialsRepository accountCredentialsRepository;

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder version(ProviderVersion version) {
      this.version = version;
      return this;
    }

    Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    Builder accountType(String accountType) {
      this.accountType = accountType;
      return this;
    }

    Builder context(String context) {
      this.context = context;
      return this;
    }

    Builder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    Builder user(String user) {
      this.user = user;
      return this;
    }

    Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile;
      return this;
    }

    Builder serviceAccount(Boolean serviceAccount) {
      this.serviceAccount = serviceAccount;;
      return this;
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership;
      return this;
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = Collections.emptyList();
        this.permissions = permissions;
      }
      return this;
    }

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries;
      return this;
    }

    Builder namespaces(List<String> namespaces) {
      this.namespaces = namespaces;
      return this;
    }

    Builder omitNamespaces(List<String> omitNamespaces) {
      this.omitNamespaces = omitNamespaces;
      return this;
    }

    Builder cacheThreads(int cacheThreads) {
      this.cacheThreads = cacheThreads;
      return this;
    }

    Builder credentials(C credentials) {
      this.credentials = credentials;
      return this;
    }

    Builder spectatorRegistry(Registry spectatorRegistry) {
      this.spectatorRegistry = spectatorRegistry;
      return this;
    }

    Builder accountCredentialsRepository(AccountCredentialsRepository accountCredentialsRepository) {
      this.accountCredentialsRepository = accountCredentialsRepository;
      return this;
    }

    private C buildCredentials() {
      switch (version) {
        case v1:
          return (C) new KubernetesV1Credentials(
              name,
              kubeconfigFile,
              context,
              cluster,
              user,
              userAgent,
              serviceAccount,
              namespaces,
              omitNamespaces,
              dockerRegistries,
              spectatorRegistry,
              accountCredentialsRepository
          );
        case v2:
          return (C) new KubernetesV2Credentials(name, spectatorRegistry);
        default:
          throw new IllegalArgumentException("Unknown provider type: " + version);
      }
    }

    KubernetesNamedAccountCredentials build() {
      if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if ((omitNamespaces != null && !omitNamespaces.isEmpty()) && (namespaces != null && !namespaces.isEmpty())) {
        throw new IllegalArgumentException("At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (cacheThreads == 0) {
        cacheThreads = 1;
      }

      if (version == null) {
        version = v1;
      }

      if (StringUtils.isEmpty(kubeconfigFile)) {
        kubeconfigFile = System.getProperty("user.home") + "/.kube/config";
      }

      if (requiredGroupMembership != null && !requiredGroupMembership.isEmpty()) {
        requiredGroupMembership = Collections.unmodifiableList(requiredGroupMembership);
      } else {
        requiredGroupMembership = Collections.emptyList();
      }

      if (credentials == null) {
        credentials = buildCredentials();
      }

      return new KubernetesNamedAccountCredentials(
          name,
          version,
          accountCredentialsRepository,
          userAgent,
          environment,
          accountType,
          context,
          cluster,
          user,
          kubeconfigFile,
          serviceAccount,
          namespaces,
          omitNamespaces,
          cacheThreads,
          dockerRegistries,
          requiredGroupMembership,
          permissions,
          spectatorRegistry,
          credentials
      );
    }
  }
}
