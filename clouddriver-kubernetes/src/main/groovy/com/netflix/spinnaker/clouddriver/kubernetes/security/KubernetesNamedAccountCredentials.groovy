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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.client.Config

public class KubernetesNamedAccountCredentials implements AccountCredentials<KubernetesCredentials> {
  final String cloudProvider = "kubernetes"
  final String name
  final String environment
  final String accountType
  final String context
  final String cluster
  final String user
  final String userAgent
  final String kubeconfigFile
  List<String> namespaces
  final int cacheThreads
  KubernetesCredentials credentials
  final List<String> requiredGroupMembership
  final List<LinkedDockerRegistryConfiguration> dockerRegistries
  private final AccountCredentialsRepository accountCredentialsRepository

  public KubernetesNamedAccountCredentials(String name,
                                           AccountCredentialsRepository accountCredentialsRepository,
                                           String userAgent,
                                           String environment,
                                           String accountType,
                                           String context,
                                           String cluster,
                                           String user,
                                           String kubeconfigFile,
                                           List<String> namespaces,
                                           int cacheThreads,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries,
                                           List<String> requiredGroupMembership,
                                           KubernetesCredentials credentials) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.context = context
    this.cluster = cluster
    this.user = user
    this.userAgent = userAgent
    this.kubeconfigFile = kubeconfigFile
    this.namespaces = namespaces
    this.cacheThreads = cacheThreads
    this.requiredGroupMembership = requiredGroupMembership
    this.dockerRegistries = dockerRegistries
    this.accountCredentialsRepository = accountCredentialsRepository
    this.credentials = credentials
  }

  public List<String> getNamespaces() {
    return credentials.getNamespaces()
  }

  static class Builder {
    String name
    String environment
    String accountType
    String context
    String cluster
    String user
    String userAgent
    String kubeconfigFile
    List<String> namespaces
    int cacheThreads
    KubernetesCredentials credentials
    List<String> requiredGroupMembership
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    AccountCredentialsRepository accountCredentialsRepository

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder context(String context) {
      this.context = context
      return this
    }

    Builder cluster(String cluster) {
      this.cluster = cluster
      return this
    }

    Builder user(String user) {
      this.user = user
      return this
    }

    Builder userAgent(String userAgent) {
      this.userAgent = userAgent
      return this
    }

    Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries
      return this
    }

    Builder namespaces(List<String> namespaces) {
      this.namespaces = namespaces
      return this
    }

    Builder cacheThreads(int cacheThreads) {
      this.cacheThreads = cacheThreads
      return this
    }

    Builder credentials(KubernetesCredentials credentials) {
      this.credentials = credentials
      return this
    }

    Builder accountCredentialsRepository(AccountCredentialsRepository accountCredentialsRepository) {
      this.accountCredentialsRepository = accountCredentialsRepository
      return this
    }

    private KubernetesCredentials buildCredentials() {
      Config config = KubernetesConfigParser.parse(kubeconfigFile, context, cluster, user, namespaces)
      config.setUserAgent(userAgent)

      for (LinkedDockerRegistryConfiguration registry : dockerRegistries) {
        if (registry.getNamespaces() == null || registry.getNamespaces().isEmpty()) {
          registry.setNamespaces(namespaces)
        }
      }

      return new KubernetesCredentials(
          new KubernetesApiAdaptor(name, config),
          namespaces,
          dockerRegistries,
          accountCredentialsRepository,
      )
    }

    KubernetesNamedAccountCredentials build() {
      if (!name) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.")
      }
      if (!dockerRegistries || dockerRegistries.size() == 0) {
        throw new IllegalArgumentException("Docker registries for Kubernetes account " + name + " missing.")
      }
      kubeconfigFile = kubeconfigFile != null && kubeconfigFile.length() > 0 ?
          kubeconfigFile :
          System.getProperty("user.home") + "/.kube/config"
      requiredGroupMembership = requiredGroupMembership ? Collections.unmodifiableList(requiredGroupMembership) : []
      def credentials = this.credentials ? this.credentials : buildCredentials() // this sets 'namespaces' if none are passed in,
                                                          // which is why 'buildCredentials()' is called here instead of below
      new KubernetesNamedAccountCredentials(
          name,
          accountCredentialsRepository,
          userAgent,
          environment,
          accountType,
          context,
          cluster,
          user,
          kubeconfigFile,
          namespaces,
          cacheThreads,
          dockerRegistries,
          requiredGroupMembership,
          credentials
      )
    }
  }
}
