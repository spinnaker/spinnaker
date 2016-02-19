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
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KubernetesNamedAccountCredentials implements AccountCredentials<KubernetesCredentials> {
  public KubernetesNamedAccountCredentials(AccountCredentialsRepository accountCredentialsRepository,
                                           String accountName,
                                           String environment,
                                           String accountType,
                                           String cluster,
                                           String user,
                                           String kubeConfigFile,
                                           List<String> namespaces,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries) {
    this(accountCredentialsRepository, accountName, environment, accountType, cluster, user, kubeConfigFile, namespaces, dockerRegistries, null);
  }

  public KubernetesNamedAccountCredentials(AccountCredentialsRepository accountCredentialsRepository,
                                           String accountName,
                                           String environment,
                                           String accountType,
                                           String cluster,
                                           String user,
                                           String kubeConfigFile,
                                           List<String> namespaces,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries,
                                           List<String> requiredGroupMembership) {
    if (accountName == null || accountName.isEmpty()) {
      throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
    }
    if (cluster == null || cluster.isEmpty()) {
      throw new IllegalArgumentException("Cluster for Kubernetes account " + accountName + " missing.");
    }
    if (user == null || user.isEmpty()) {
      throw new IllegalArgumentException("User for Kubernetes account " + accountName + " missing.");
    }
    if (dockerRegistries == null || dockerRegistries.size() == 0) {
      throw new IllegalArgumentException("Docker registries for Kubernetes account " + accountName + " missing.");
    }
    this.accountName = accountName;
    this.environment = environment;
    this.accountType = accountType;
    this.cluster = cluster;
    this.user = user;
    this.kubeConfigFile = kubeConfigFile != null && kubeConfigFile.length() > 0 ?
      kubeConfigFile : System.getProperty("user.home") + "/.kube/config";
    this.namespaces = (namespaces == null || namespaces.size() == 0) ? Arrays.asList("default") : namespaces;
    // TODO(lwander): what is this?
    this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
    this.dockerRegistries = dockerRegistries;

    for (int i = 0; i < this.dockerRegistries.size(); i++) {
      LinkedDockerRegistryConfiguration registry = this.dockerRegistries.get(i);
      if (registry.getNamespaces() == null || registry.getNamespaces().size() == 0) {
        registry.setNamespaces(this.namespaces);
      }
    }

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
    Config config = KubernetesConfigParser.parse(this.kubeConfigFile, this.cluster, this.user, this.namespaces.get(0));
    KubernetesClient client;
    try {
      client = new DefaultKubernetesClient(config);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create credentials.", e);
    }
    return new KubernetesCredentials(new KubernetesApiAdaptor(client), this.namespaces, this.dockerRegistries, this.accountCredentialsRepository);
  }

  private static String getLocalName(String fullUrl) {
    return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
  }

  @Override
  public String getProvider() {
    return getCloudProvider();
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
  private final String cluster;
  private final String user;
  private final String kubeConfigFile;
  private final List<String> namespaces;
  private final KubernetesCredentials credentials;
  private final List<String> requiredGroupMembership;
  private final List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final AccountCredentialsRepository accountCredentialsRepository;
}
