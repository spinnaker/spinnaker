/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.run.v1.model.GoogleCloudRunV1Condition;
import com.google.api.services.run.v1.model.ObjectMeta;
import com.google.api.services.run.v1.model.Revision;
import com.google.api.services.run.v1.model.RevisionStatus;
import com.google.api.services.run.v1.model.Service;
import com.google.api.services.run.v1.model.ServiceStatus;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudrunServerGroupCachingAgentTest {
  private static final String ACCOUNT = "my-account";

  private CloudrunServerGroupCachingAgent agent;

  @BeforeEach
  void setUp() {
    agent =
        new CloudrunServerGroupCachingAgent(
            ACCOUNT,
            mock(CloudrunNamedAccountCredentials.class),
            new ObjectMapper(),
            new NoopRegistry());
  }

  /**
   * SqlCache only persists item bodies for AUTHORITATIVE types; INFORMATIVE types only get
   * relationship rows. This agent is the sole producer of every namespace it caches, so each of
   * them must be AUTHORITATIVE or the data is lost and reads return null (e.g. a load balancer
   * lookup during the clusters view).
   */
  @Test
  void everyCachedNamespaceIsAuthoritative() {
    CacheResult result = agent.buildCacheResult(servicesWithOneRevision(), Map.of(), List.of(), 0L);

    Map<String, AgentDataType.Authority> authorities = new HashMap<>();
    agent.getProvidedDataTypes().forEach(t -> authorities.put(t.getTypeName(), t.getAuthority()));

    Map<String, Collection<CacheData>> populated = new HashMap<>();
    result.getCacheResults().forEach((ns, data) -> populated.put(ns, data));

    for (String ns :
        List.of(
            Keys.Namespace.APPLICATIONS.getNs(),
            Keys.Namespace.CLUSTERS.getNs(),
            Keys.Namespace.SERVER_GROUPS.getNs(),
            Keys.Namespace.INSTANCES.getNs(),
            Keys.Namespace.LOAD_BALANCERS.getNs())) {
      assertThat(populated.get(ns)).as("cached entries for %s", ns).isNotEmpty();
      assertThat(authorities.get(ns))
          .as("authority for %s", ns)
          .isEqualTo(AgentDataType.Authority.AUTHORITATIVE);
    }
  }

  @Test
  void loadBalancerIsCachedWithItsBody() {
    CacheResult result = agent.buildCacheResult(servicesWithOneRevision(), Map.of(), List.of(), 0L);

    Collection<CacheData> loadBalancers =
        result.getCacheResults().get(Keys.Namespace.LOAD_BALANCERS.getNs());

    assertThat(loadBalancers).hasSize(1);
    CacheData lb = loadBalancers.iterator().next();
    assertThat(lb.getId()).isEqualTo(Keys.getLoadBalancerKey(ACCOUNT, "my-service"));
    assertThat(lb.getAttributes().get("loadBalancer")).isInstanceOf(CloudrunLoadBalancer.class);
    assertThat(
            ((CloudrunLoadBalancer) lb.getAttributes().get("loadBalancer"))
                .getLatestReadyRevisionName())
        .isEqualTo("my-service-00001");
  }

  private static Map<Service, List<Revision>> servicesWithOneRevision() {
    Service service =
        new Service()
            .setMetadata(
                new ObjectMeta()
                    .setName("my-service")
                    .setNamespace("123456")
                    .setAnnotations(Map.of("spinnaker/application", "myapp")))
            .setStatus(
                new ServiceStatus()
                    .setUrl("https://my-service.example.com")
                    .setLatestReadyRevisionName("my-service-00001")
                    .setLatestCreatedRevisionName("my-service-00001"));

    Revision revision =
        new Revision()
            .setMetadata(
                new ObjectMeta()
                    .setName("my-service-00001")
                    .setCreationTimestamp("2026-01-01T00:00:00Z")
                    .setAnnotations(Map.of("autoscaling.knative.dev/maxScale", "10"))
                    .setLabels(
                        Map.of(
                            "serving.knative.dev/service", "my-service",
                            "cloud.googleapis.com/location", "us-central1")))
            .setStatus(
                new RevisionStatus()
                    .setConditions(
                        List.of(
                            new GoogleCloudRunV1Condition().setType("Active").setStatus("True"))));

    Map<Service, List<Revision>> byService = new HashMap<>();
    byService.put(service, new ArrayList<>(List.of(revision)));
    return byService;
  }

  @Test
  void onDemandRefreshIgnoresRequestsForOtherAccountsAndWithoutAServerGroup() {
    assertThat(agent.handle(null, Map.of("serverGroupName", "app-v001", "account", "other")))
        .isNull();
    assertThat(agent.handle(null, Map.of("account", ACCOUNT))).isNull();
  }
}
