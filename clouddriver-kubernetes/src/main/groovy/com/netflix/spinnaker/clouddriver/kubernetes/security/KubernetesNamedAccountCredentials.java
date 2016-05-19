/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Collections;
import java.util.List;

public class KubernetesNamedAccountCredentials implements AccountCredentials<KubernetesCredentials> {
  public KubernetesNamedAccountCredentials(AccountCredentialsRepository accountCredentialsRepository,
                                           String userAgent,
                                           String accountName,
                                           String environment,
                                           String accountType,
                                           String context,
                                           String cluster,
                                           String user,
                                           String kubeconfigFile,
                                           List<String> namespaces,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries) {
    this(accountCredentialsRepository, userAgent, accountName, environment, accountType, context, cluster, user, kubeconfigFile, namespaces, dockerRegistries, null);
  }

  public KubernetesNamedAccountCredentials(AccountCredentialsRepository accountCredentialsRepository,
                                           String userAgent,
                                           String accountName,
                                           String environment,
                                           String accountType,
                                           String context,
                                           String cluster,
                                           String user,
                                           String kubeconfigFile,
                                           List<String> namespaces,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries,
                                           List<String> requiredGroupMembership) {
    if (accountName == null || accountName.isEmpty()) {
      throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
    }
    if (dockerRegistries == null || dockerRegistries.size() == 0) {
      throw new IllegalArgumentException("Docker registries for Kubernetes account " + accountName + " missing.");
    }
    this.accountName = accountName;
    this.environment = environment;
    this.accountType = accountType;
    this.context = context;
    this.cluster = cluster;
    this.user = user;
    this.userAgent = userAgent;
    this.kubeconfigFile = kubeconfigFile != null && kubeconfigFile.length() > 0 ?
      kubeconfigFile : System.getProperty("user.home") + "/.kube/config";
    this.namespaces = namespaces;
    // TODO(lwander): what is this?
    this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
    this.dockerRegistries = dockerRegistries;

    this.accountCredentialsRepository = accountCredentialsRepository;
    this.credentials = buildCredentials();
  }

  @Override
  public String getName() {
    return accountName;
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
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return dockerRegistries;
  }

  public KubernetesCredentials getCredentials() {
    return credentials;
  }

  public List<String> getNamespaces() {
    return namespaces;
  }

  private KubernetesCredentials buildCredentials() {
    Config config = KubernetesConfigParser.parse(kubeconfigFile, context, cluster, user, namespaces);
    config.setUserAgent(userAgent);
    if (namespaces == null || namespaces.isEmpty()) {
      namespaces = Collections.singletonList(config.getNamespace());
    }

    for (LinkedDockerRegistryConfiguration registry : dockerRegistries) {
      if (registry.getNamespaces() == null || registry.getNamespaces().isEmpty()) {
        registry.setNamespaces(namespaces);
      }
    }

    KubernetesClient client;
    try {
      client = new DefaultKubernetesClient(config);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create credentials.", e);
    }

    return new KubernetesCredentials(new KubernetesApiAdaptor(client), namespaces, dockerRegistries, accountCredentialsRepository);
  }

  private static String getLocalName(String fullUrl) {
    return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
  }

  public String getAccountName() {
    return accountName;
  }

  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  private static final String CLOUD_PROVIDER = "kubernetes";
  private final String accountName;
  private final String environment;
  private final String accountType;
  private final String context;
  private final String cluster;
  private final String user;
  private final String userAgent;
  private final String kubeconfigFile;
  private List<String> namespaces;
  private final KubernetesCredentials credentials;
  private final List<String> requiredGroupMembership;
  private List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final AccountCredentialsRepository accountCredentialsRepository;
}
