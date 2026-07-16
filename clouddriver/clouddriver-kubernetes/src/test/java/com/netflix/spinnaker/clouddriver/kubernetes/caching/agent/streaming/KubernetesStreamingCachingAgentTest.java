/*
 * Copyright 2025 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ApplicationCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ClusterCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.BaseKubernetesCachingAgentTest;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KubernetesStreamingCachingAgentTest extends BaseKubernetesCachingAgentTest {

  private static final String DEPLOYMENT_KEY =
      InfrastructureCacheKey.createKey(
          KubernetesKind.DEPLOYMENT, ACCOUNT, NAMESPACE1, DEPLOYMENT_NAME);
  private static final String REPLICA_SET_KEY =
      InfrastructureCacheKey.createKey(
          KubernetesKind.REPLICA_SET, ACCOUNT, NAMESPACE1, REPLICA_SET_NAME);
  private static final String POD_KEY =
      InfrastructureCacheKey.createKey(KubernetesKind.POD, ACCOUNT, NAMESPACE1, POD_NAME);
  private static final String APPLICATION_KEY = ApplicationCacheKey.createKey(MONIKER_APPLICATION);
  private static final String CLUSTER_KEY =
      ClusterCacheKey.createKey(ACCOUNT, MONIKER_APPLICATION, MONIKER_CLUSTER);

  @Test
  void buildCacheResult_emptyData_returnsEmptyCacheResult() {
    CacheResult cacheResult = buildCacheResult(List.of(), List.of());

    assertThat(cacheResult).isNotNull();
    assertThat(cacheResult.getCacheResults()).isEmpty();
    assertThat(cacheResult.getEvictions()).isEmpty();
  }

  @Test
  void buildCacheResult_deployment_returnsDeploymentAndLogicalKinds() {
    CacheResult cacheResult =
        buildCacheResult(List.of(deploymentManifest(DEPLOYMENT_NAME)), List.of());

    assertThat(cacheResult).isNotNull();
    assertThat(cacheResult.getEvictions()).isEmpty();

    assertThat(cacheResult.getCacheResults().keySet())
        .containsExactlyInAnyOrder(DEPLOYMENT_KIND, APPLICATION_KIND, CLUSTER_KIND);

    // deployment
    Collection<CacheData> deployments = cacheResult.getCacheResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).hasSize(1);
    CacheData deployment = deployments.iterator().next();
    assertThat(deployment.getId()).isEqualTo(DEPLOYMENT_KEY);
    assertThat(deployment.getAttributes().get("name")).isEqualTo(DEPLOYMENT_NAME);

    // application
    Collection<CacheData> applications = cacheResult.getCacheResults().get(APPLICATION_KIND);
    assertThat(applications).hasSize(1);
    CacheData application = applications.iterator().next();
    assertThat(application.getId()).isEqualTo(APPLICATION_KEY);
    assertThat(application.getAttributes().get("name")).isEqualTo("my-application");

    // cluster
    Collection<CacheData> clusters = cacheResult.getCacheResults().get(CLUSTER_KIND);
    assertThat(clusters).hasSize(1);
    CacheData cluster = clusters.iterator().next();
    assertThat(cluster.getId()).isEqualTo(CLUSTER_KEY);
    assertThat(cluster.getAttributes().get("name")).isEqualTo("my-cluster");
  }

  @Test
  void buildCacheResult_logicalRelationships_returnsDeploymentWithRelationshipsToLogicalKind() {
    CacheResult cacheResult =
        buildCacheResult(List.of(deploymentManifest(DEPLOYMENT_NAME)), List.of());

    assertThat(cacheResult).isNotNull();
    assertThat(cacheResult.getEvictions()).isEmpty();

    assertThat(cacheResult.getCacheResults().keySet())
        .containsExactlyInAnyOrder(DEPLOYMENT_KIND, APPLICATION_KIND, CLUSTER_KIND);

    // deployment
    CacheData deployment = cacheResult.getCacheResults().get(DEPLOYMENT_KIND).iterator().next();
    assertThat(deployment.getRelationships())
        .containsEntry("applications", Set.of(APPLICATION_KEY))
        .containsEntry("clusters", Set.of(CLUSTER_KEY));

    // application
    CacheData application = cacheResult.getCacheResults().get(APPLICATION_KIND).iterator().next();
    assertThat(application.getRelationships())
        .containsEntry("deployment", Set.of(DEPLOYMENT_KEY))
        .containsEntry("clusters", Set.of(CLUSTER_KEY));

    // cluster
    CacheData cluster = cacheResult.getCacheResults().get(CLUSTER_KIND).iterator().next();
    assertThat(cluster.getRelationships())
        .containsEntry("applications", Set.of(APPLICATION_KEY))
        .containsEntry("deployment", Set.of(DEPLOYMENT_KEY));
  }

  @Test
  void buildCacheResult_ownerReferences_returnsObjectsWithRelationships() {
    CacheResult cacheResult =
        buildCacheResult(
            List.of(
                deploymentManifest(DEPLOYMENT_NAME),
                replicaSetManifest(REPLICA_SET_NAME, DEPLOYMENT_NAME),
                podManifest(POD_NAME, REPLICA_SET_NAME)),
            List.of());

    assertThat(cacheResult).isNotNull();
    assertThat(cacheResult.getEvictions()).isEmpty();

    assertThat(cacheResult.getCacheResults().keySet())
        .contains(DEPLOYMENT_KIND, REPLICA_SET_KIND, POD_KIND);

    // pod has a relationship to the replicaSet
    CacheData pod = cacheResult.getCacheResults().get(POD_KIND).iterator().next();
    assertThat(pod.getRelationships()).containsEntry("replicaSet", Set.of(REPLICA_SET_KEY));

    // replicaset has a relationship to the deployment
    CacheData replicaset = cacheResult.getCacheResults().get(REPLICA_SET_KIND).iterator().next();
    assertThat(replicaset.getRelationships()).containsEntry("deployment", Set.of(DEPLOYMENT_KEY));
  }

  @Test
  void buildCacheResult_deletedObject_returnsEviction() {
    CacheResult cacheResult =
        buildCacheResult(List.of(), List.of(deploymentManifest(DEPLOYMENT_NAME)));

    assertThat(cacheResult).isNotNull();
    assertThat(cacheResult.getCacheResults()).isEmpty();

    assertThat(cacheResult.getEvictions().keySet()).containsExactlyInAnyOrder(DEPLOYMENT_KIND);

    // deployment
    Collection<String> evictedDeployments = cacheResult.getEvictions().get(DEPLOYMENT_KIND);
    assertThat(evictedDeployments).containsExactly(DEPLOYMENT_KEY);
  }

  @Test
  void buildCacheResult_upsertAndDelete_returnsUpsertedAndDeleted() {
    CacheResult cacheResult =
        buildCacheResult(
            List.of(deploymentManifest(DEPLOYMENT_NAME)), List.of(podManifest(POD_NAME, null)));

    assertThat(cacheResult).isNotNull();

    // upsert
    assertThat(cacheResult.getCacheResults().keySet())
        .containsExactlyInAnyOrder(DEPLOYMENT_KIND, APPLICATION_KIND, CLUSTER_KIND);
    Collection<CacheData> deployments = cacheResult.getCacheResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).hasSize(1);
    CacheData deployment = deployments.iterator().next();
    assertThat(deployment.getId()).isEqualTo(DEPLOYMENT_KEY);

    // evict
    assertThat(cacheResult.getEvictions().keySet()).containsExactlyInAnyOrder(POD_KIND);
    Collection<String> evictedDeployments = cacheResult.getEvictions().get(POD_KIND);
    assertThat(evictedDeployments).containsExactly(POD_KEY);
  }

  @Test
  void buildCacheResult_configuredNamespaces_returnsOnlyObjectsFromAllowedNamespaces() {
    KubernetesManifest deployment1 = deploymentManifest(DEPLOYMENT_NAME);

    KubernetesManifest deployment2 = new KubernetesManifest();
    deployment2.put("metadata", new HashMap<>());
    deployment2.getAnnotations().put("moniker.spinnaker.io/cluster", MONIKER_CLUSTER);
    deployment2.getAnnotations().put("moniker.spinnaker.io/application", MONIKER_APPLICATION);
    deployment2.setNamespace("unknown-namespace");
    deployment2.setKind(KubernetesKind.DEPLOYMENT);
    deployment2.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment2.setName("deployment-2");

    // cluster scoped resource will be cached
    KubernetesManifest clusterRole = new KubernetesManifest();
    clusterRole.put("metadata", new HashMap<>());
    clusterRole.setKind(KubernetesKind.CLUSTER_ROLE);
    clusterRole.setApiVersion(KubernetesApiVersion.fromString("rbac.authorization.k8s.io/v1"));
    clusterRole.setName("my-cluster-role");

    CacheResult cacheResult =
        buildCacheResult(List.of(deployment1, deployment2, clusterRole), List.of());

    assertThat(cacheResult).isNotNull();

    // contains only the deployment in the configured namespace
    Collection<CacheData> deployments = cacheResult.getCacheResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).hasSize(1);
    CacheData deployment = deployments.iterator().next();
    assertThat(deployment.getId()).isEqualTo(DEPLOYMENT_KEY);

    // contains the cluster scoped resource
    Collection<CacheData> clusterRoles =
        cacheResult.getCacheResults().get(KubernetesKind.CLUSTER_ROLE.toString());
    assertThat(clusterRoles).hasSize(1);
    CacheData clusterRoleData = clusterRoles.iterator().next();
    assertThat(clusterRoleData.getId())
        .isEqualTo(
            InfrastructureCacheKey.createKey(
                KubernetesKind.CLUSTER_ROLE, ACCOUNT, null, "my-cluster-role"));
  }

  @Test
  void buildCacheResult_configuredNamespaces_evictsAllNamespaces() {
    KubernetesManifest deployment1 = deploymentManifest(DEPLOYMENT_NAME);

    KubernetesManifest deployment2 = new KubernetesManifest();
    deployment2.put("metadata", new HashMap<>());
    deployment2.getAnnotations().put("moniker.spinnaker.io/cluster", MONIKER_CLUSTER);
    deployment2.getAnnotations().put("moniker.spinnaker.io/application", MONIKER_APPLICATION);
    deployment2.setNamespace("unknown-namespace");
    deployment2.setKind(KubernetesKind.DEPLOYMENT);
    deployment2.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment2.setName("deployment-2");

    // cluster scoped resource
    KubernetesManifest clusterRole = new KubernetesManifest();
    clusterRole.put("metadata", new HashMap<>());
    clusterRole.setKind(KubernetesKind.CLUSTER_ROLE);
    clusterRole.setApiVersion(KubernetesApiVersion.fromString("rbac.authorization.k8s.io/v1"));
    clusterRole.setName("my-cluster-role");

    CacheResult cacheResult =
        buildCacheResult(List.of(), List.of(deployment1, deployment2, clusterRole));

    assertThat(cacheResult).isNotNull();

    // evict resources from all namespaces even if they are not in the configured namespace
    assertThat(cacheResult.getEvictions().get(DEPLOYMENT_KIND))
        .containsExactly(
            DEPLOYMENT_KEY,
            "kubernetes.v2:infrastructure:deployment:my-account:unknown-namespace:deployment-2");
    assertThat(cacheResult.getEvictions().get(KubernetesKind.CLUSTER_ROLE.toString()))
        .containsExactly("kubernetes.v2:infrastructure:clusterRole:my-account::my-cluster-role");
  }

  private static CacheResult buildCacheResult(
      List<KubernetesManifest> upserted, List<KubernetesManifest> deleted) {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    KubernetesNamedAccountCredentials namedAccountCredentials = getNamedAccountCredentials();
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(namedAccountCredentials, configurationProperties);

    return cachingAgent.buildCacheResult(upserted, deleted);
  }

  @Test
  void filteredPrimaryKinds_cacheAll_returnsAllRegisteredKinds() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);
    KubernetesNamedAccountCredentials namedAccountCredentials = getNamedAccountCredentials();
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(namedAccountCredentials, configurationProperties);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    assertThat(filteredPrimaryKinds)
        .containsExactlyInAnyOrderElementsOf(
            kindProperties.keySet()); // has everything in global kinds
  }

  @Test
  void filteredPrimaryKinds_kindsInConfig_returnsOnlySpecifiedKinds() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(false);
    configurationProperties
        .getCache()
        .setCacheKinds(List.of("deployment", "myCustomKind.my.group"));
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(getNamedAccountCredentials(), configurationProperties);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    assertThat(filteredPrimaryKinds)
        .containsExactlyInAnyOrder(
            KubernetesKind.fromString(
                "deployment")); // only has core kinds, don't support custom kinds yet
  }

  @Test
  void filteredPrimaryKinds_defaultConfig_returnsOnlySpinnakerUIKinds() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(getNamedAccountCredentials(), configurationProperties);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    KubernetesKind[] expected =
        KubernetesCachingAgent.SPINNAKER_UI_KINDS.stream()
            .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
            .flatMap(Collection::stream)
            .filter(kindProperties::containsKey)
            .toArray(KubernetesKind[]::new);
    assertThat(filteredPrimaryKinds).containsExactlyInAnyOrder(expected); // only has UI kinds
  }

  @Test
  void filteredPrimaryKinds_omitKinds_returnsSpinnakerUIKindsExceptOmitted() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheOmitKinds(List.of("deployment"));
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(getNamedAccountCredentials(), configurationProperties);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    KubernetesKind[] expected =
        KubernetesCachingAgent.SPINNAKER_UI_KINDS.stream()
            .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
            .flatMap(Collection::stream)
            .filter(kindProperties::containsKey)
            .filter(k -> !k.equals(KubernetesKind.DEPLOYMENT))
            .toArray(KubernetesKind[]::new);
    assertThat(filteredPrimaryKinds).containsExactlyInAnyOrder(expected); // excludes Deployment
  }

  private static KubernetesStreamingCachingAgent createCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      KubernetesConfigurationProperties configurationProperties) {
    return new KubernetesStreamingCachingAgent(
        namedAccountCredentials,
        configurationProperties,
        kubernetesSpinnakerKindMap,
        null,
        new NoopRegistry());
  }
}
