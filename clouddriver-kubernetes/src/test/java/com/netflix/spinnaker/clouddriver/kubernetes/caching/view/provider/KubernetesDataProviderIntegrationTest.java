/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory;
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesApplication;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesCluster;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesInstance;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesLoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesServerGroupManager;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesServerGroupSummary;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider.Sort;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.AccountResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesNamerRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesPodHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ManifestFetcher;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.security.GlobalKubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager.ServerGroupManagerSummary;
import com.netflix.spinnaker.clouddriver.model.ServerGroupSummary;
import com.netflix.spinnaker.clouddriver.search.SearchResultSet;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.internal.stubbing.defaultanswers.ReturnsSmartNulls;

@ExtendWith(SoftAssertionsExtension.class)
@RunWith(JUnitPlatform.class)
final class KubernetesDataProviderIntegrationTest {
  private static final String ACCOUNT_NAME = "my-account";
  private static final Registry registry = new NoopRegistry();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final KubernetesProvider kubernetesProvider = new KubernetesProvider();
  private static final KubernetesCachingAgentDispatcher dispatcher =
      new KubernetesCachingAgentDispatcher(objectMapper, registry);
  private static final ImmutableList<KubernetesHandler> handlers =
      ImmutableList.of(
          new KubernetesDeploymentHandler(),
          new KubernetesReplicaSetHandler(),
          new KubernetesServiceHandler(),
          new KubernetesPodHandler());
  private static final KubernetesSpinnakerKindMap kindMap =
      new KubernetesSpinnakerKindMap(handlers);
  private static final GlobalResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          handlers, new KubernetesUnregisteredCustomResourceHandler());
  private static final AccountCredentialsRepository credentialsRepository =
      new MapBackedAccountCredentialsRepository();
  private static final KubernetesAccountResolver accountResolver =
      new KubernetesAccountResolver(credentialsRepository, resourcePropertyRegistry);
  private static final ProviderRegistry providerRegistry =
      new DefaultProviderRegistry(
          ImmutableList.of(kubernetesProvider), new InMemoryNamedCacheFactory());
  private static final KubernetesCacheUtils cacheUtils =
      new KubernetesCacheUtils(
          providerRegistry.getProviderCache(kubernetesProvider.getProviderName()),
          kindMap,
          accountResolver);
  private static final ImmutableSetMultimap<String, String> manifestsByNamespace =
      ImmutableSetMultimap.<String, String>builder()
          .putAll(
              "backend-ns",
              ImmutableSet.of(
                  "backend-service.yml",
                  "backend-rs-014.yml",
                  "backend-pod-014.yml",
                  "backend-rs-015.yml",
                  "backend-pod-015.yml"))
          .putAll(
              "frontend-ns",
              ImmutableSet.of(
                  "frontend-service.yml",
                  "frontend-deployment.yml",
                  "frontend-rs-old.yml",
                  "frontend-rs-new.yml",
                  "frontend-pod-1.yml",
                  "frontend-pod-2.yml"))
          .build();

  private static KubernetesApplicationProvider applicationProvider =
      new KubernetesApplicationProvider(cacheUtils);
  private static KubernetesClusterProvider clusterProvider =
      new KubernetesClusterProvider(cacheUtils);
  private static KubernetesInstanceProvider instanceProvider =
      new KubernetesInstanceProvider(cacheUtils, accountResolver);
  private static KubernetesLoadBalancerProvider loadBalancerProvider =
      new KubernetesLoadBalancerProvider(cacheUtils);
  private static KubernetesSearchProvider searchProvider =
      new KubernetesSearchProvider(cacheUtils, kindMap, objectMapper, accountResolver);
  private static KubernetesServerGroupManagerProvider serverGroupManagerProvider =
      new KubernetesServerGroupManagerProvider(cacheUtils);
  private static ArtifactProvider artifactProvider = new ArtifactProvider();
  private static KubernetesManifestProvider manifestProvider =
      new KubernetesManifestProvider(accountResolver);

  private static KubernetesNamedAccountCredentials credentials = getNamedAccountCredentials();

  @BeforeAll
  static void prepareCache() {
    credentialsRepository.save(credentials.getName(), credentials);
    dispatcher
        .buildAllCachingAgents(credentials)
        .forEach(agent -> agent.getAgentExecution(providerRegistry).executeAgent(agent));
  }

  @Test
  void getClusters(SoftAssertions softly) {
    Map<String, Set<KubernetesCluster>> results = clusterProvider.getClusters();
    assertThat(results).hasSize(1);
    assertThat(results).containsKey(ACCOUNT_NAME);

    Set<KubernetesCluster> clusters = results.get(ACCOUNT_NAME);
    assertThat(clusters).hasSize(2);

    assertThat(clusters)
        .extracting(KubernetesCluster::getName)
        .containsExactlyInAnyOrder("deployment frontend", "replicaSet backend");

    Map<String, KubernetesCluster> clusterLookup =
        clusters.stream().collect(toImmutableMap(KubernetesCluster::getName, c -> c));

    assertFrontendCluster(softly, clusterLookup.get("deployment frontend"), true);
    assertBackendCluster(softly, clusterLookup.get("replicaSet backend"), true);
  }

  @Test
  void getClustersForApplication(SoftAssertions softly) {
    Map<String, Set<KubernetesCluster>> results = clusterProvider.getClusterDetails("backendapp");
    assertThat(results).hasSize(1);
    assertThat(results).containsKey(ACCOUNT_NAME);

    Set<KubernetesCluster> clusters = results.get(ACCOUNT_NAME);
    assertThat(clusters).hasSize(1);

    assertThat(clusters)
        .extracting(KubernetesCluster::getName)
        .containsExactlyInAnyOrder("replicaSet backend");

    Map<String, KubernetesCluster> clusterLookup =
        clusters.stream().collect(toImmutableMap(KubernetesCluster::getName, c -> c));

    assertBackendCluster(softly, clusterLookup.get("replicaSet backend"), true);
  }

  @Test
  void getClustersForApplicationAndAccount(SoftAssertions softly) {
    Set<KubernetesCluster> clusters = clusterProvider.getClusters("backendapp", ACCOUNT_NAME);
    assertThat(clusters).hasSize(1);

    assertThat(clusters)
        .extracting(KubernetesCluster::getName)
        .containsExactlyInAnyOrder("replicaSet backend");

    Map<String, KubernetesCluster> clusterLookup =
        clusters.stream().collect(toImmutableMap(KubernetesCluster::getName, c -> c));

    assertBackendCluster(softly, clusterLookup.get("replicaSet backend"), true);
  }

  @Test
  void getClustersForApplicationAndWrongAccount(SoftAssertions softly) {
    Set<KubernetesCluster> clusters = clusterProvider.getClusters("backendapp", "non-existent");
    assertThat(clusters).hasSize(0);
  }

  @Test
  void getSingleCluster(SoftAssertions softly) {
    // When not explicitly passing the includeDetails flag, it should default to true.
    KubernetesCluster cluster =
        clusterProvider.getCluster("frontendapp", ACCOUNT_NAME, "deployment frontend");
    assertThat(cluster).isNotNull();
    assertFrontendCluster(softly, cluster, true);
  }

  @Test
  void getSingleClusterWithDetails(SoftAssertions softly) {
    KubernetesCluster cluster =
        clusterProvider.getCluster("frontendapp", ACCOUNT_NAME, "deployment frontend", true);
    assertThat(cluster).isNotNull();
    assertFrontendCluster(softly, cluster, true);
  }

  @Test
  void getSingleClusterWithoutDetails(SoftAssertions softly) {
    KubernetesCluster cluster =
        clusterProvider.getCluster("frontendapp", ACCOUNT_NAME, "deployment frontend", false);
    assertThat(cluster).isNotNull();
    assertFrontendCluster(softly, cluster, false);
  }

  @Test
  void getSingleClusterWrongApp(SoftAssertions softly) {
    KubernetesCluster cluster =
        clusterProvider.getCluster("backendapp", ACCOUNT_NAME, "deployment frontend");
    assertThat(cluster).isNull();
  }

  @Test
  void getClusterSummaries(SoftAssertions softly) {
    Map<String, Set<KubernetesCluster>> results = clusterProvider.getClusterSummaries("backendapp");
    assertThat(results).hasSize(1);
    assertThat(results).containsKey(ACCOUNT_NAME);

    Set<KubernetesCluster> clusters = results.get(ACCOUNT_NAME);
    assertThat(clusters).hasSize(1);

    assertThat(clusters)
        .extracting(KubernetesCluster::getName)
        .containsExactlyInAnyOrder("replicaSet backend");

    Map<String, KubernetesCluster> clusterLookup =
        clusters.stream().collect(toImmutableMap(KubernetesCluster::getName, c -> c));

    assertBackendCluster(softly, clusterLookup.get("replicaSet backend"), false);
  }

  @Test
  void getServerGroup(SoftAssertions softly) {
    KubernetesServerGroup serverGroup =
        clusterProvider.getServerGroup(ACCOUNT_NAME, "backend-ns", "replicaSet backend-v014");
    assertThat(serverGroup).isNotNull();
    assertBackendPriorServerGroup(softly, serverGroup);
  }

  @Test
  void getServerGroupWithManager(SoftAssertions softly) {
    KubernetesServerGroup serverGroup =
        clusterProvider.getServerGroup(
            ACCOUNT_NAME, "frontend-ns", "replicaSet frontend-5c6559f75f");
    assertThat(serverGroup).isNotNull();
    assertFrontendCurrentServerGroup(softly, serverGroup);
  }

  @Test
  void getServerGroupWrongNamespace(SoftAssertions softly) {
    KubernetesServerGroup serverGroup =
        clusterProvider.getServerGroup(ACCOUNT_NAME, "frontend-ns", "replicaSet backend-v014");
    assertThat(serverGroup).isNull();
  }

  @Test
  void getServerGroupWithDetails(SoftAssertions softly) {
    KubernetesServerGroup serverGroup =
        clusterProvider.getServerGroup(ACCOUNT_NAME, "backend-ns", "replicaSet backend-v014", true);
    assertThat(serverGroup).isNotNull();
    assertBackendPriorServerGroup(softly, serverGroup);
  }

  @Test
  void getServerGroupWithoutDetails(SoftAssertions softly) {
    KubernetesServerGroup serverGroup =
        clusterProvider.getServerGroup(
            ACCOUNT_NAME, "backend-ns", "replicaSet backend-v014", false);
    assertThat(serverGroup).isNotNull();
    // Looks like we ignore the includeDetails flag, so this is the same serverGroup as when we do
    // include details.
    assertBackendPriorServerGroup(softly, serverGroup);
  }

  @Test
  void getApplicationsUnexpanded(SoftAssertions softly) {
    Set<KubernetesApplication> result = applicationProvider.getApplications(false);
    softly.assertThat(result).hasSize(2);

    Map<String, KubernetesApplication> applicationLookup =
        result.stream().collect(toImmutableMap(Application::getName, a -> a));

    KubernetesApplication frontendApplication = applicationLookup.get("frontendapp");
    softly.assertThat(frontendApplication).isNotNull();
    if (frontendApplication != null) {
      assertFrontendApplication(softly, frontendApplication);
    }

    KubernetesApplication backendApplication = applicationLookup.get("backendapp");
    softly.assertThat(frontendApplication).isNotNull();
    if (frontendApplication != null) {
      assertBackendApplication(softly, backendApplication);
    }
  }

  @Test
  void getApplicationsExpanded(SoftAssertions softly) {
    // This is the same as the unexpanded test, as it seems like we ignore the flag.
    Set<KubernetesApplication> result = applicationProvider.getApplications(true);
    softly.assertThat(result).hasSize(2);

    Map<String, KubernetesApplication> applicationLookup =
        result.stream().collect(toImmutableMap(Application::getName, a -> a));

    KubernetesApplication frontendApplication = applicationLookup.get("frontendapp");
    softly.assertThat(frontendApplication).isNotNull();
    if (frontendApplication != null) {
      assertFrontendApplication(softly, frontendApplication);
    }

    KubernetesApplication backendApplication = applicationLookup.get("backendapp");
    softly.assertThat(frontendApplication).isNotNull();
    if (frontendApplication != null) {
      assertBackendApplication(softly, backendApplication);
    }
  }

  @Test
  void getApplication(SoftAssertions softly) {
    KubernetesApplication result = applicationProvider.getApplication("backendapp");
    assertThat(result).isNotNull();
    assertBackendApplication(softly, result);
  }

  @Test
  void getInstance(SoftAssertions softly) {
    KubernetesInstance result =
        instanceProvider.getInstance(ACCOUNT_NAME, "backend-ns", "pod backend-v015-vhglj");
    assertThat(result).isNotNull();
    assertBackendCurrentServerGroupInstance(softly, result);
  }

  @Test
  void getServerGroupManagers(SoftAssertions softly) {
    Set<KubernetesServerGroupManager> results =
        serverGroupManagerProvider.getServerGroupManagersByApplication("frontendapp");
    assertThat(results).hasSize(1);
    if (!results.isEmpty()) {
      assertFrontEndServerGroupManager(softly, results.iterator().next());
    }
  }

  @Test
  void getApplicationLoadBalancers(SoftAssertions softly) {
    Set<KubernetesLoadBalancer> results =
        loadBalancerProvider.getApplicationLoadBalancers("frontendapp");
    assertThat(results).hasSize(1);
    assertFrontendLoadBalancer(softly, results.iterator().next());
  }

  @Test
  void getLoadBalancersByName(SoftAssertions softly) {
    List<KubernetesLoadBalancer> results =
        loadBalancerProvider.byAccountAndRegionAndName(
            ACCOUNT_NAME, "frontend-ns", "service frontend");
    assertThat(results).hasSize(1);
    assertFrontendLoadBalancer(softly, results.iterator().next());
  }

  @Test
  void searchBackendReplicaSet(SoftAssertions softly) {
    SearchResultSet resultSet = searchProvider.search("backend-v014", 1, 100);

    softly.assertThat(resultSet.getQuery()).isEqualTo("backend-v014");
    softly.assertThat(resultSet.getTotalMatches()).isEqualTo(2);

    List<Map<String, Object>> results = resultSet.getResults();
    softly.assertThat(results).hasSize(2);

    Optional<Map<String, Object>> optionalRs =
        results.stream().filter(r -> r.get("name").equals("replicaSet backend-v014")).findFirst();
    softly.assertThat(optionalRs).isPresent();
    optionalRs.ifPresent(
        rs ->
            softly
                .assertThat(rs)
                .containsAllEntriesOf(
                    ImmutableMap.<String, String>builder()
                        .put("account", ACCOUNT_NAME)
                        .put("group", "replicaSet")
                        .put("kubernetesKind", "replicaSet")
                        .put("name", "replicaSet backend-v014")
                        .put("namespace", "backend-ns")
                        .put("provider", "kubernetes")
                        .put("region", "backend-ns")
                        .put("serverGroup", "replicaSet backend-v014")
                        .put("type", "serverGroups")
                        .build()));

    Optional<Map<String, Object>> optionalPod =
        results.stream().filter(r -> r.get("name").equals("pod backend-v014-xkvwh")).findFirst();
    softly.assertThat(optionalPod).isPresent();
    optionalPod.ifPresent(
        pod ->
            softly
                .assertThat(pod)
                .containsAllEntriesOf(
                    ImmutableMap.<String, String>builder()
                        .put("account", ACCOUNT_NAME)
                        .put("group", "pod")
                        .put("instanceId", "pod backend-v014-xkvwh")
                        .put("kubernetesKind", "pod")
                        .put("name", "pod backend-v014-xkvwh")
                        .put("namespace", "backend-ns")
                        .put("provider", "kubernetes")
                        .put("region", "backend-ns")
                        .put("type", "instances")
                        .build()));
  }

  @Test
  void getArtifacts(SoftAssertions softly) {
    List<Artifact> artifacts =
        artifactProvider.getArtifacts(
            KubernetesKind.REPLICA_SET, "backend", "backend-ns", credentials.getCredentials());
    softly.assertThat(artifacts).hasSize(2);
    softly
        .assertThat(artifacts)
        .allSatisfy(
            artifact -> {
              softly.assertThat(artifact.getType()).isEqualTo("kubernetes/replicaSet");
              softly.assertThat(artifact.getName()).isEqualTo("backend");
              softly.assertThat(artifact.getLocation()).isEqualTo("backend-ns");
              softly
                  .assertThat(Optional.ofNullable((String) artifact.getMetadata("account")))
                  .contains(ACCOUNT_NAME);
            });
    // Order matters here because we're expecting to get the artifacts back in the order they were
    // created.
    softly.assertThat(artifacts).extracting(Artifact::getVersion).containsExactly("v014", "v015");
  }

  @Test
  void getArtifactsWrongType(SoftAssertions softly) {
    List<Artifact> artifacts =
        artifactProvider.getArtifacts(
            KubernetesKind.DEPLOYMENT, "backend", "backend-ns", credentials.getCredentials());
    softly.assertThat(artifacts).isEmpty();
  }

  @Test
  void getArtifactsWrongNamespace(SoftAssertions softly) {
    List<Artifact> artifacts =
        artifactProvider.getArtifacts(
            KubernetesKind.REPLICA_SET, "backend", "frontend-ns", credentials.getCredentials());
    softly.assertThat(artifacts).isEmpty();
  }

  @Test
  void getClusterAndSortAscending(SoftAssertions softly) {
    List<KubernetesManifest> manifests =
        manifestProvider.getClusterAndSortAscending(
            ACCOUNT_NAME, "backend-ns", "replicaSet", "replicaSet backend", "backendapp", Sort.AGE);
    assertThat(manifests).isNotNull();
    softly
        .assertThat(
            manifests.stream()
                .map(KubernetesManifest::getFullResourceName)
                .collect(toImmutableList()))
        .containsExactly("replicaSet backend-v014", "replicaSet backend-v015");
  }

  @Test
  void getClusterAndSortAscendingBadAccount(SoftAssertions softly) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manifestProvider.getClusterAndSortAscending(
                "not-an-account",
                "backend-ns",
                "replicaSet",
                "replicaSet backend",
                "backendapp",
                Sort.AGE));
  }

  @Test
  void getClusterManifestCoordinates(SoftAssertions softly) {
    List<KubernetesCoordinates> coordinates =
        manifestProvider.getClusterManifestCoordinates(
            ACCOUNT_NAME, "backend-ns", "replicaSet", "backendapp", "replicaSet backend");
    assertThat(coordinates).isNotNull();
    softly
        .assertThat(coordinates.stream().collect(toImmutableList()))
        .containsExactlyInAnyOrder(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.REPLICA_SET)
                .name("backend-v014")
                .namespace("backend-ns")
                .build(),
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.REPLICA_SET)
                .name("backend-v015")
                .namespace("backend-ns")
                .build());
  }

  @Test
  void getClusterManifestCoordinatesBadAccount(SoftAssertions softly) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manifestProvider.getClusterManifestCoordinates(
                "not-an-account", "backend-ns", "replicaSet", "backendapp", "replicaSet backend"));
  }

  @Test
  void getClusterManifestCoordinatesEmptyNamespace(SoftAssertions softly) {
    List<KubernetesCoordinates> coordinates =
        manifestProvider.getClusterManifestCoordinates(
            ACCOUNT_NAME, "empty", "replicaSet", "backendapp", "replicaSet backend");
    softly.assertThat(coordinates).isEmpty();
  }

  @Test
  void getClusterManifestCoordinatesEmptyCluster(SoftAssertions softly) {
    List<KubernetesCoordinates> coordinates =
        manifestProvider.getClusterManifestCoordinates(
            ACCOUNT_NAME, "empty-namespace", "replicaSet", "backendapp", "replicaSet empty");
    softly.assertThat(coordinates).isEmpty();
  }

  private static KubectlJobExecutor getJobExecutor() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class, new ReturnsSmartNulls());
    when(jobExecutor.list(
            any(KubernetesCredentials.class),
            anyList(),
            any(String.class),
            any(KubernetesSelectorList.class)))
        .thenAnswer(
            invocation ->
                manifestsByNamespace.get(invocation.getArgument(2, String.class)).stream()
                    .map(
                        file ->
                            ManifestFetcher.getManifest(
                                KubernetesDataProviderIntegrationTest.class, file))
                    .filter(m -> invocation.getArgument(1, List.class).contains(m.getKind()))
                    .collect(toImmutableList()));
    return jobExecutor;
  }

  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName(ACCOUNT_NAME);
    managedAccount.setNamespaces(manifestsByNamespace.keySet().asList());
    managedAccount.setKinds(ImmutableList.of("deployment", "replicaSet", "service", "pod"));
    managedAccount.setMetrics(false);

    KubernetesCredentials.Factory credentialFactory =
        new KubernetesCredentials.Factory(
            new NoopRegistry(),
            new KubernetesNamerRegistry(ImmutableList.of(new KubernetesManifestNamer())),
            getJobExecutor(),
            new ConfigFileService(new CloudConfigResourceService()),
            new AccountResourcePropertyRegistry.Factory(resourcePropertyRegistry),
            new KubernetesKindRegistry.Factory(new GlobalKubernetesKindRegistry()),
            kindMap);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  private void assertFrontendLoadBalancer(
      SoftAssertions softly, KubernetesLoadBalancer loadBalancer) {
    softly.assertThat(loadBalancer.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(loadBalancer.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly
        .assertThat(loadBalancer.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly.assertThat(loadBalancer.getKind()).isEqualTo(KubernetesKind.SERVICE);
    softly.assertThat(loadBalancer.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(loadBalancer.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(loadBalancer.getMoniker().getCluster()).isEqualTo("service frontend");
    softly.assertThat(loadBalancer.getName()).isEqualTo("service frontend");
    assertFrontendLoadBalancerServerGroups(softly, loadBalancer.getServerGroups());
  }

  private void assertFrontendCluster(
      SoftAssertions softly, KubernetesCluster cluster, boolean includeDetails) {
    softly.assertThat(cluster.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(cluster.getMoniker().getCluster()).isEqualTo("deployment frontend");
    softly.assertThat(cluster.getType()).isEqualTo("kubernetes");
    softly.assertThat(cluster.getAccountName()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(cluster.getName()).isEqualTo("deployment frontend");
    softly.assertThat(cluster.getApplication()).isEqualTo("frontendapp");

    if (includeDetails) {
      assertFrontendServerGroups(softly, cluster.getServerGroups());
      softly.assertThat(cluster.getLoadBalancers()).hasSize(1);
      if (!cluster.getLoadBalancers().isEmpty()) {
        assertFrontendLoadBalancer(softly, cluster.getLoadBalancers().iterator().next());
      }
    } else {
      softly.assertThat(cluster.getServerGroups()).isEmpty();
      softly.assertThat(cluster.getLoadBalancers()).isEmpty();
    }
  }

  private void assertFrontendServerGroups(
      SoftAssertions softly, Collection<KubernetesServerGroup> serverGroups) {
    softly.assertThat(serverGroups).hasSize(2);
    softly
        .assertThat(serverGroups)
        .extracting(ServerGroup::getName)
        .containsExactlyInAnyOrder(
            "replicaSet frontend-5c6559f75f", "replicaSet frontend-64545c4c54");
    Map<String, KubernetesServerGroup> serverGroupLookup =
        serverGroups.stream().collect(toImmutableMap(ServerGroup::getName, sg -> sg));

    KubernetesServerGroup currentServerGroup =
        serverGroupLookup.get("replicaSet frontend-5c6559f75f");
    softly.assertThat(currentServerGroup).isNotNull();
    // If the soft assertion already failed; don't NPE trying to validate further.
    if (currentServerGroup != null) {
      assertFrontendCurrentServerGroup(softly, currentServerGroup);
    }

    KubernetesServerGroup priorServerGroup =
        serverGroupLookup.get("replicaSet frontend-64545c4c54");
    softly.assertThat(currentServerGroup).isNotNull();
    // If the soft assertion already failed; don't NPE trying to validate further.
    if (currentServerGroup != null) {
      assertFrontendPriorServerGroup(softly, priorServerGroup);
    }
  }

  private void assertFrontendPriorServerGroup(
      SoftAssertions softly, KubernetesServerGroup serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("deployment frontend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(1);
    softly.assertThat(serverGroup.getCapacity().getDesired()).isEqualTo(0);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getKind()).isEqualTo(KubernetesKind.REPLICA_SET);
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-64545c4c54");
    softly.assertThat(serverGroup.getInstanceCounts().getUp()).isEqualTo(0);
    softly.assertThat(serverGroup.getInstanceCounts().getTotal()).isEqualTo(0);
    softly.assertThat(serverGroup.getLoadBalancers()).containsExactly("service frontend");
    // When using a deployment, the prior server group is not disabled as labels aren't changed;
    // instead this server group is scaled down to 0 instances.
    softly.assertThat(serverGroup.isDisabled()).isFalse();
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(serverGroup.getServerGroupManagers()).hasSize(1);
    if (!serverGroup.getServerGroupManagers().isEmpty()) {
      assertFrontEndServerGroupManagerSummary(
          softly, serverGroup.getServerGroupManagers().iterator().next());
    }
    softly
        .assertThat(serverGroup.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly
        .assertThat((Collection<String>) serverGroup.getBuildInfo().get("images"))
        .containsExactly("nginx:1.19.0");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getInstances()).isEmpty();
  }

  private void assertFrontendCurrentServerGroup(
      SoftAssertions softly, KubernetesServerGroup serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("deployment frontend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(2);
    softly.assertThat(serverGroup.getCapacity().getDesired()).isEqualTo(2);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getKind()).isEqualTo(KubernetesKind.REPLICA_SET);
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-5c6559f75f");
    softly.assertThat(serverGroup.getInstanceCounts().getUp()).isEqualTo(2);
    softly.assertThat(serverGroup.getInstanceCounts().getTotal()).isEqualTo(2);
    softly.assertThat(serverGroup.getLoadBalancers()).containsExactly("service frontend");
    softly.assertThat(serverGroup.isDisabled()).isFalse();
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(serverGroup.getServerGroupManagers()).hasSize(1);
    if (!serverGroup.getServerGroupManagers().isEmpty()) {
      assertFrontEndServerGroupManagerSummary(
          softly, serverGroup.getServerGroupManagers().iterator().next());
    }
    softly
        .assertThat(serverGroup.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly
        .assertThat((Collection<String>) serverGroup.getBuildInfo().get("images"))
        .containsExactly("nginx:1.19.1");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    assertFrontendCurrentServerGroupInstances(softly, serverGroup.getInstances());
  }

  private void assertFrontendCurrentServerGroupInstances(
      SoftAssertions softly, Collection<KubernetesInstance> instances) {
    softly.assertThat(instances).hasSize(2);
    Map<String, KubernetesInstance> instanceLookup =
        instances.stream().collect(toImmutableMap(Instance::getName, i -> i));

    KubernetesInstance firstInstance = instanceLookup.get("477dcf19-be44-4853-88fd-1d9aedfcddba");
    softly.assertThat(firstInstance).isNotNull();
    if (firstInstance != null) {
      assertFrontendFirstInstance(softly, firstInstance);
    }

    KubernetesInstance secondInstance = instanceLookup.get("a2280982-e745-468f-9176-21ff1642fa8d");
    softly.assertThat(firstInstance).isNotNull();
    if (secondInstance != null) {
      assertFrontendSecondInstance(softly, secondInstance);
    }
  }

  private void assertFrontendFirstInstance(SoftAssertions softly, KubernetesInstance instance) {
    softly.assertThat(instance.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(instance.getZone()).isEqualTo("frontend-ns");
    softly.assertThat(instance.getKind()).isEqualTo(KubernetesKind.POD);
    softly.assertThat(instance.getHealthState()).isEqualTo(HealthState.Up);
    softly.assertThat(instance.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(instance.getHumanReadableName()).isEqualTo("pod frontend-5c6559f75f-4ml8h");
    softly.assertThat(instance.getName()).isEqualTo("477dcf19-be44-4853-88fd-1d9aedfcddba");
    softly
        .assertThat(instance.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "load-balancer", "frontend",
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker",
                "app", "nginx"));
    softly.assertThat(instance.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(instance.getMoniker().getCluster()).isEqualTo("deployment frontend");
  }

  private void assertFrontendSecondInstance(SoftAssertions softly, KubernetesInstance instance) {
    softly.assertThat(instance.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(instance.getZone()).isEqualTo("frontend-ns");
    softly.assertThat(instance.getKind()).isEqualTo(KubernetesKind.POD);
    softly.assertThat(instance.getHealthState()).isEqualTo(HealthState.Up);
    softly.assertThat(instance.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(instance.getHumanReadableName()).isEqualTo("pod frontend-5c6559f75f-6fdmt");
    softly.assertThat(instance.getName()).isEqualTo("a2280982-e745-468f-9176-21ff1642fa8d");
    softly
        .assertThat(instance.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "load-balancer", "frontend",
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker",
                "app", "nginx"));
    softly.assertThat(instance.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(instance.getMoniker().getCluster()).isEqualTo("deployment frontend");
  }

  private void assertFrontEndServerGroupManagerSummary(
      SoftAssertions softly, ServerGroupManagerSummary summary) {
    softly.assertThat(summary.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(summary.getLocation()).isEqualTo("frontend-ns");
    softly.assertThat(summary.getName()).isEqualTo("frontend");
  }

  private void assertFrontEndServerGroupManager(
      SoftAssertions softly, KubernetesServerGroupManager serverGroupManager) {
    softly.assertThat(serverGroupManager.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroupManager.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(serverGroupManager.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(serverGroupManager.getName()).isEqualTo("deployment frontend");
    softly.assertThat(serverGroupManager.getKind()).isEqualTo(KubernetesKind.DEPLOYMENT);
    softly.assertThat(serverGroupManager.getMoniker().getApp()).isEqualTo("frontendapp");
    softly
        .assertThat(serverGroupManager.getMoniker().getCluster())
        .isEqualTo("deployment frontend");
    softly
        .assertThat(serverGroupManager.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "frontendapp",
                "app.kubernetes.io/managed-by", "spinnaker"));
    assertFrontendServerGroupSummaries(softly, serverGroupManager.getServerGroups());
  }

  private void assertFrontendLoadBalancerServerGroups(
      SoftAssertions softly, Collection<LoadBalancerServerGroup> serverGroups) {
    softly.assertThat(serverGroups).hasSize(2);
    Map<String, LoadBalancerServerGroup> serverGroupLookup =
        serverGroups.stream().collect(toImmutableMap(LoadBalancerServerGroup::getName, sg -> sg));

    LoadBalancerServerGroup priorServerGroup =
        serverGroupLookup.get("replicaSet frontend-64545c4c54");
    softly.assertThat(priorServerGroup).isNotNull();
    if (priorServerGroup != null) {
      assertFrontendPriorLoadBalancerServerGroup(softly, priorServerGroup);
    }

    LoadBalancerServerGroup currentServerGroup =
        serverGroupLookup.get("replicaSet frontend-5c6559f75f");
    softly.assertThat(currentServerGroup).isNotNull();
    if (currentServerGroup != null) {
      assertFrontendCurrentLoadBalancerServerGroup(softly, currentServerGroup);
    }
  }

  private void assertFrontendPriorLoadBalancerServerGroup(
      SoftAssertions softly, LoadBalancerServerGroup serverGroup) {
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-64545c4c54");
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getIsDisabled()).isFalse();
    softly.assertThat(serverGroup.getDetachedInstances()).isEmpty();
    softly.assertThat(serverGroup.getInstances()).isEmpty();
  }

  private void assertFrontendCurrentLoadBalancerServerGroup(
      SoftAssertions softly, LoadBalancerServerGroup serverGroup) {
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-5c6559f75f");
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getIsDisabled()).isFalse();
    softly.assertThat(serverGroup.getDetachedInstances()).isEmpty();
    softly.assertThat(serverGroup.getInstances()).hasSize(2);
    softly
        .assertThat(serverGroup.getInstances())
        .extracting(LoadBalancerInstance::getName)
        .containsExactlyInAnyOrder(
            "pod frontend-5c6559f75f-4ml8h", "pod frontend-5c6559f75f-6fdmt");
    softly
        .assertThat(serverGroup.getInstances())
        .allMatch(sg -> sg.getZone().equals("frontend-ns"));
  }

  private void assertFrontendServerGroupSummaries(
      SoftAssertions softly, Collection<KubernetesServerGroupSummary> serverGroups) {
    softly.assertThat(serverGroups).hasSize(2);
    Map<String, ServerGroupSummary> serverGroupLookup =
        serverGroups.stream().collect(toImmutableMap(ServerGroupSummary::getName, sg -> sg));

    ServerGroupSummary priorServerGroup = serverGroupLookup.get("replicaSet frontend-64545c4c54");
    softly.assertThat(priorServerGroup).isNotNull();
    if (priorServerGroup != null) {
      assertFrontendPriorServerGroupSummary(softly, priorServerGroup);
    }

    ServerGroupSummary currentServerGroup = serverGroupLookup.get("replicaSet frontend-5c6559f75f");
    softly.assertThat(currentServerGroup).isNotNull();
    if (currentServerGroup != null) {
      assertFrontendCurrentServerGroupSummary(softly, currentServerGroup);
    }
  }

  private void assertFrontendPriorServerGroupSummary(
      SoftAssertions softly, ServerGroupSummary serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("deployment frontend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(1);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-64545c4c54");
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
  }

  private void assertFrontendCurrentServerGroupSummary(
      SoftAssertions softly, ServerGroupSummary serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("frontendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("deployment frontend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(2);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet frontend-5c6559f75f");
    softly.assertThat(serverGroup.getRegion()).isEqualTo("frontend-ns");
  }

  private void assertBackendLoadBalancer(
      SoftAssertions softly, KubernetesLoadBalancer loadBalancer) {
    softly.assertThat(loadBalancer.getRegion()).isEqualTo("backend-ns");
    softly.assertThat(loadBalancer.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly
        .assertThat(loadBalancer.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "backendapp",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly.assertThat(loadBalancer.getKind()).isEqualTo(KubernetesKind.SERVICE);
    softly.assertThat(loadBalancer.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(loadBalancer.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(loadBalancer.getMoniker().getCluster()).isEqualTo("service backendlb");
    softly.assertThat(loadBalancer.getName()).isEqualTo("service backendlb");
    assertBackendLoadBalancerServerGroups(softly, loadBalancer.getServerGroups());
  }

  private void assertBackendLoadBalancerServerGroups(
      SoftAssertions softly, Collection<LoadBalancerServerGroup> serverGroups) {
    softly.assertThat(serverGroups).hasSize(1);

    if (!serverGroups.isEmpty()) {
      LoadBalancerServerGroup serverGroup = serverGroups.iterator().next();
      assertBackendLoadBalancerServerGroup(softly, serverGroup);
    }
  }

  private void assertBackendLoadBalancerServerGroup(
      SoftAssertions softly, LoadBalancerServerGroup serverGroup) {
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet backend-v015");
    softly.assertThat(serverGroup.getRegion()).isEqualTo("backend-ns");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getIsDisabled()).isFalse();
    softly.assertThat(serverGroup.getDetachedInstances()).isEmpty();
    softly.assertThat(serverGroup.getInstances()).hasSize(1);
    if (!serverGroup.getInstances().isEmpty()) {
      assertBackendLoadBalancerInstance(softly, serverGroup.getInstances().iterator().next());
    }
  }

  private void assertBackendLoadBalancerInstance(
      SoftAssertions softly, LoadBalancerInstance instance) {
    softly.assertThat(instance.getName()).isEqualTo("pod backend-v015-vhglj");
    softly.assertThat(instance.getZone()).isEqualTo("backend-ns");
  }

  private void assertBackendCluster(
      SoftAssertions softly, KubernetesCluster cluster, boolean includeDetails) {
    softly.assertThat(cluster.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(cluster.getMoniker().getCluster()).isEqualTo("replicaSet backend");
    softly.assertThat(cluster.getType()).isEqualTo("kubernetes");
    softly.assertThat(cluster.getAccountName()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(cluster.getName()).isEqualTo("replicaSet backend");
    softly.assertThat(cluster.getApplication()).isEqualTo("backendapp");

    if (includeDetails) {
      assertBackendServerGroups(softly, cluster.getServerGroups());
      softly.assertThat(cluster.getLoadBalancers()).hasSize(1);
      // If soft assertion above already failed, don't try to further validate.
      if (!cluster.getLoadBalancers().isEmpty()) {
        assertBackendLoadBalancer(softly, cluster.getLoadBalancers().iterator().next());
      }
    } else {
      softly.assertThat(cluster.getServerGroups()).isEmpty();
      softly.assertThat(cluster.getLoadBalancers()).isEmpty();
    }
  }

  private void assertBackendServerGroups(
      SoftAssertions softly, Collection<KubernetesServerGroup> serverGroups) {
    softly.assertThat(serverGroups).hasSize(2);
    softly
        .assertThat(serverGroups)
        .extracting(ServerGroup::getName)
        .containsExactlyInAnyOrder("replicaSet backend-v014", "replicaSet backend-v015");
    Map<String, KubernetesServerGroup> serverGroupLookup =
        serverGroups.stream().collect(toImmutableMap(ServerGroup::getName, sg -> sg));

    KubernetesServerGroup currentServerGroup = serverGroupLookup.get("replicaSet backend-v015");
    softly.assertThat(currentServerGroup).isNotNull();
    // If the soft assertion already failed; don't NPE trying to validate further.
    if (currentServerGroup != null) {
      assertBackendCurrentServerGroup(softly, currentServerGroup);
    }

    KubernetesServerGroup priorServerGroup = serverGroupLookup.get("replicaSet backend-v014");
    softly.assertThat(priorServerGroup).isNotNull();
    // If the soft assertion already failed; don't NPE trying to validate further.
    if (priorServerGroup != null) {
      assertBackendPriorServerGroup(softly, priorServerGroup);
    }
  }

  private void assertBackendPriorServerGroup(
      SoftAssertions softly, KubernetesServerGroup serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("replicaSet backend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(14);
    softly.assertThat(serverGroup.getCapacity().getDesired()).isEqualTo(1);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getKind()).isEqualTo(KubernetesKind.REPLICA_SET);
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet backend-v014");
    softly.assertThat(serverGroup.getInstanceCounts().getUp()).isEqualTo(1);
    softly.assertThat(serverGroup.getInstanceCounts().getTotal()).isEqualTo(1);
    softly.assertThat(serverGroup.getLoadBalancers()).containsExactly("service backendlb");
    // When using a replica set with traffic management, the prior server group is disabled.
    softly.assertThat(serverGroup.isDisabled()).isTrue();
    softly.assertThat(serverGroup.getRegion()).isEqualTo("backend-ns");
    softly.assertThat(serverGroup.getServerGroupManagers()).isEmpty();
    softly
        .assertThat(serverGroup.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "backendapp",
                "moniker.spinnaker.io/sequence", "14",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly
        .assertThat((Collection<String>) serverGroup.getBuildInfo().get("images"))
        .containsExactly(
            "gcr.io/my-gcr-repository/backend-service@sha256:2eefbb528a4619311555f92ea9b781af101c62f4c70b73c4a5e93d15624ba94c");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getInstances()).hasSize(1);
    if (!serverGroup.getInstances().isEmpty()) {
      assertBackendPriorServerGroupInstance(softly, serverGroup.getInstances().iterator().next());
    }
  }

  void assertBackendCurrentServerGroup(SoftAssertions softly, KubernetesServerGroup serverGroup) {
    softly.assertThat(serverGroup.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(serverGroup.getMoniker().getCluster()).isEqualTo("replicaSet backend");
    softly.assertThat(serverGroup.getMoniker().getSequence()).isEqualTo(15);
    softly.assertThat(serverGroup.getCapacity().getDesired()).isEqualTo(1);
    softly.assertThat(serverGroup.getAccount()).isEqualTo("my-account");
    softly.assertThat(serverGroup.getKind()).isEqualTo(KubernetesKind.REPLICA_SET);
    softly.assertThat(serverGroup.getName()).isEqualTo("replicaSet backend-v015");
    softly.assertThat(serverGroup.getInstanceCounts().getUp()).isEqualTo(1);
    softly.assertThat(serverGroup.getInstanceCounts().getTotal()).isEqualTo(1);
    softly.assertThat(serverGroup.getLoadBalancers()).containsExactly("service backendlb");
    softly.assertThat(serverGroup.isDisabled()).isFalse();
    softly.assertThat(serverGroup.getRegion()).isEqualTo("backend-ns");
    softly.assertThat(serverGroup.getServerGroupManagers()).isEmpty();
    softly
        .assertThat(serverGroup.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "backendapp",
                "moniker.spinnaker.io/sequence", "15",
                "app.kubernetes.io/managed-by", "spinnaker"));
    softly
        .assertThat((Collection<String>) serverGroup.getBuildInfo().get("images"))
        .containsExactly(
            "gcr.io/my-gcr-repository/backend-service@sha256:51f29a570a484fbae4da912199ff27ed21f91b1caf51564a9d3afe3a201c1f32");
    softly.assertThat(serverGroup.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(serverGroup.getInstances()).hasSize(1);
    if (!serverGroup.getInstances().isEmpty()) {
      assertBackendCurrentServerGroupInstance(softly, serverGroup.getInstances().iterator().next());
    }
  }

  private void assertBackendPriorServerGroupInstance(
      SoftAssertions softly, KubernetesInstance instance) {
    softly.assertThat(instance.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(instance.getZone()).isEqualTo("backend-ns");
    softly.assertThat(instance.getKind()).isEqualTo(KubernetesKind.POD);
    softly.assertThat(instance.getHealthState()).isEqualTo(HealthState.Up);
    softly.assertThat(instance.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(instance.getHumanReadableName()).isEqualTo("pod backend-v014-xkvwh");
    softly.assertThat(instance.getName()).isEqualTo("d05606fe-aa69-4f16-b56a-371c2313fe9c");
    softly
        .assertThat(instance.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "backendapp",
                "app.kubernetes.io/managed-by", "spinnaker",
                "app", "nginx"));
    softly.assertThat(instance.getLabels()).doesNotContainEntry("load-balancer", "backend");
    softly.assertThat(instance.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(instance.getMoniker().getCluster()).isEqualTo("replicaSet backend");
  }

  private void assertBackendCurrentServerGroupInstance(
      SoftAssertions softly, KubernetesInstance instance) {
    softly.assertThat(instance.getAccount()).isEqualTo(ACCOUNT_NAME);
    softly.assertThat(instance.getZone()).isEqualTo("backend-ns");
    softly.assertThat(instance.getKind()).isEqualTo(KubernetesKind.POD);
    softly.assertThat(instance.getHealthState()).isEqualTo(HealthState.Up);
    softly.assertThat(instance.getCloudProvider()).isEqualTo("kubernetes");
    softly.assertThat(instance.getHumanReadableName()).isEqualTo("pod backend-v015-vhglj");
    softly.assertThat(instance.getName()).isEqualTo("45db7673-e3d2-4746-9ecd-38f868f853e5");
    softly
        .assertThat(instance.getLabels())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "app.kubernetes.io/name", "backendapp",
                "app.kubernetes.io/managed-by", "spinnaker",
                "app", "nginx"));
    softly.assertThat(instance.getLabels()).containsEntry("load-balancer", "backend");
    softly.assertThat(instance.getMoniker().getApp()).isEqualTo("backendapp");
    softly.assertThat(instance.getMoniker().getCluster()).isEqualTo("replicaSet backend");
  }

  void assertFrontendApplication(SoftAssertions softly, KubernetesApplication application) {
    softly.assertThat(application.getName()).isEqualTo("frontendapp");
    softly
        .assertThat(application.getAttributes())
        .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("name", "frontendapp"));
    softly.assertThat(application.getClusterNames()).hasSize(1);

    Set<String> clusterNames = application.getClusterNames().get(ACCOUNT_NAME);
    softly.assertThat(clusterNames).isNotNull();
    if (clusterNames != null) {
      softly.assertThat(clusterNames).containsExactlyInAnyOrder("deployment frontend");
    }
  }

  void assertBackendApplication(SoftAssertions softly, KubernetesApplication application) {
    softly.assertThat(application.getName()).isEqualTo("backendapp");
    softly
        .assertThat(application.getAttributes())
        .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("name", "backendapp"));
    softly.assertThat(application.getClusterNames()).hasSize(1);

    Set<String> clusterNames = application.getClusterNames().get(ACCOUNT_NAME);
    softly.assertThat(clusterNames).isNotNull();
    if (clusterNames != null) {
      softly.assertThat(clusterNames).containsExactlyInAnyOrder("replicaSet backend");
    }
  }
}
