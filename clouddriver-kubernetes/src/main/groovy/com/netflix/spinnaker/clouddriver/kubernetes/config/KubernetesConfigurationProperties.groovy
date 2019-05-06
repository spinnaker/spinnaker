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

package com.netflix.spinnaker.clouddriver.kubernetes.config

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.ToString

@ToString(includeNames = true)
class KubernetesConfigurationProperties {
  private static final Integer DEFAULT_CACHE_THREADS = 1

  @ToString(includeNames = true)
  static class ManagedAccount {
    String name
    ProviderVersion providerVersion = ProviderVersion.v1
    String environment
    String accountType
    String context
    String cluster
    String oAuthServiceAccount
    List<String> oAuthScopes
    String user
    String kubeconfigFile
    String kubeconfigContents
    String kubectlExecutable
    Integer kubectlRequestTimeoutSeconds
    boolean serviceAccount = false
    boolean configureImagePullSecrets = true
    List<String> namespaces
    List<String> omitNamespaces
    String skin
    int cacheThreads = DEFAULT_CACHE_THREADS
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    List<String> requiredGroupMembership
    Permissions.Builder permissions = new Permissions.Builder()
    String namingStrategy = "kubernetesAnnotations"
    boolean debug = false
    boolean metrics = true
    boolean checkPermissionsOnStartup = true
    List<CustomKubernetesResource> customResources;
    List<KubernetesCachingPolicy> cachingPolicies;
    List<String> kinds
    List<String> omitKinds
    boolean onlySpinnakerManaged = false
    boolean liveManifestCalls = false
    Long cacheIntervalSeconds
  }

  List<ManagedAccount> accounts = []
}

@ToString(includeNames = true)
class LinkedDockerRegistryConfiguration {
  String accountName
  List<String> namespaces
}

@ToString(includeNames = true)
class CustomKubernetesResource {
  String kubernetesKind
  String spinnakerKind = KubernetesSpinnakerKindMap.SpinnakerKind.UNCLASSIFIED.toString()
  String deployPriority = "100"
  boolean versioned = false
  boolean namespaced = true
}

@ToString(includeNames = true)
class KubernetesCachingPolicy {
  String kubernetesKind
  int maxEntriesPerAgent
}
