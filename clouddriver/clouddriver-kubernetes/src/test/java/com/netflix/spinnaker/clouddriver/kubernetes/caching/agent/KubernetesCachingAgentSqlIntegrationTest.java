/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.sql.SqlProviderRegistry;
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics;
import com.netflix.spinnaker.cats.sql.cache.SqlNamedCacheFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.config.SqlConstraintsInitializer;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.sql.config.RetryProperties;
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.testcontainers.DockerClientFactory;

/**
 * Regression coverage for the stale-cache bug fixed across #8054/#8055 and this session's
 * follow-up: deletes the last live Deployment in a namespace and confirms it actually disappears
 * from a real SQL-backed cache, rather than only asserting on the in-memory {@code CacheResult}
 * returned by {@code loadData()} (as {@link KubernetesCoreCachingAgentTest} does).
 *
 * <p>This runs the real {@link KubernetesCoreCachingAgent}, the real {@link KubernetesProvider} (so
 * a regression to its {@code supportsFullEviction} opt-in is caught here too), and a real {@code
 * SqlCache}/{@code SqlProviderCache} backed by a MySQL testcontainer -- the same backend and wiring
 * (via {@code CachingAgent.CacheExecution}) production uses. Only the kubectl/API layer is mocked,
 * via {@link KubernetesCredentials}, exactly as in the module's other caching-agent tests.
 */
class KubernetesCachingAgentSqlIntegrationTest {

  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE = "test-namespace";
  private static final String DEPLOYMENT_NAME = "my-deployment";
  private static final String DEPLOYMENT_KIND = KubernetesKind.DEPLOYMENT.toString();

  private static SqlTestUtil.TestDatabase testDatabase;

  @BeforeAll
  static void setUpClass() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available; skipping SQL integration test");
    testDatabase = SqlTestUtil.initTcMysqlDatabase();
  }

  @AfterAll
  static void tearDownClass() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  @Test
  void deletingLastDeploymentInNamespaceEvictsItFromSqlCache() {
    SqlNamedCacheFactory cacheFactory =
        new SqlNamedCacheFactory(
            testDatabase.context,
            new ObjectMapper(),
            null,
            Clock.systemUTC(),
            new SqlRetryProperties(new RetryProperties(1, 10), new RetryProperties(1, 10)),
            "test",
            mock(SqlCacheMetrics.class),
            DynamicConfigService.NOOP,
            SqlConstraintsInitializer.INSTANCE.getDefaultSqlConstraints(SQLDialect.MYSQL));

    KubernetesProvider kubernetesProvider = new KubernetesProvider();
    SqlProviderRegistry providerRegistry =
        new SqlProviderRegistry(List.of(kubernetesProvider), cacheFactory);
    ProviderCache providerCache =
        providerRegistry.getProviderCache(KubernetesProvider.PROVIDER_NAME);

    AtomicBoolean includeDeployment = new AtomicBoolean(true);
    KubernetesCoreCachingAgent agent = buildAgent(includeDeployment);
    AgentExecution execution = agent.getAgentExecution(providerRegistry);

    String deploymentKey =
        Keys.InfrastructureCacheKey.createKey(
            KubernetesKind.DEPLOYMENT, ACCOUNT, NAMESPACE, DEPLOYMENT_NAME);

    // Cycle 1: the Deployment exists -- it should be cached.
    execution.executeAgent(agent);
    assertThat(providerCache.getAll(DEPLOYMENT_KIND))
        .extracting(CacheData::getId)
        .containsExactly(deploymentKey);

    // Cycle 2: the Deployment was deleted (the last one in this namespace) -- its cache entry
    // must be evicted, not left behind.
    includeDeployment.set(false);
    execution.executeAgent(agent);
    assertThat(providerCache.getAll(DEPLOYMENT_KIND)).isEmpty();
  }

  private static KubernetesCoreCachingAgent buildAgent(AtomicBoolean includeDeployment) {
    ImmutableMap<KubernetesKind, KubernetesKindProperties> kindProperties =
        ImmutableMap.of(
            KubernetesKind.DEPLOYMENT,
            KubernetesKindProperties.create(KubernetesKind.DEPLOYMENT, true));

    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getGlobalKinds()).thenReturn(kindProperties.keySet().asList());
    when(credentials.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(invocation -> kindProperties.get(invocation.getArgument(0)));
    when(credentials.getDeclaredNamespaces()).thenReturn(ImmutableList.of(NAMESPACE));
    when(credentials.getResourcePropertyRegistry())
        .thenReturn(
            new GlobalResourcePropertyRegistry(
                ImmutableList.of(new KubernetesDeploymentHandler()),
                new KubernetesUnregisteredCustomResourceHandler()));
    when(credentials.getNamer()).thenReturn(new KubernetesManifestNamer());
    when(credentials.isValidKind(any(KubernetesKind.class))).thenReturn(true);
    when(credentials.getKubernetesSpinnakerKindMap())
        .thenReturn(new KubernetesSpinnakerKindMap(List.of(new KubernetesDeploymentHandler())));

    Answer<ImmutableList<KubernetesManifest>> listAnswer =
        invocation -> {
          List<KubernetesKind> kinds = invocation.getArgument(0);
          String namespace = invocation.getArgument(1);
          if (includeDeployment.get()
              && kinds.contains(KubernetesKind.DEPLOYMENT)
              && NAMESPACE.equals(namespace)) {
            return ImmutableList.of(deploymentManifest());
          }
          return ImmutableList.of();
        };
    when(credentials.list(any(List.class), any())).thenAnswer(listAnswer);
    when(credentials.listAuthoritative(any(List.class), any())).thenAnswer(listAnswer);

    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName(ACCOUNT);
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(credentials);
    KubernetesNamedAccountCredentials namedAccountCredentials =
        new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);

    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);

    return new KubernetesCoreCachingAgent(
        namedAccountCredentials,
        new ObjectMapper(),
        new NoopRegistry(),
        0,
        1,
        10L,
        configurationProperties,
        new KubernetesSpinnakerKindMap(List.of(new KubernetesDeploymentHandler())),
        null);
  }

  private static KubernetesManifest deploymentManifest() {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.setNamespace(NAMESPACE);
    deployment.setKind(KubernetesKind.DEPLOYMENT);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(DEPLOYMENT_NAME);
    return deployment;
  }
}
