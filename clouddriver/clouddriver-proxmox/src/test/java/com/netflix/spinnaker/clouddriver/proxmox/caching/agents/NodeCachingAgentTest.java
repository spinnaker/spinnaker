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
 */
package com.netflix.spinnaker.clouddriver.proxmox.caching.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class NodeCachingAgentTest {

  private static final String ACCOUNT_NAME = "my-proxmox";
  private static final String NODE_NAME = "pve01";

  @Mock private ProxmoxNamedAccountCredentials credentials;
  @Mock private ProxmoxApiService api;
  @Mock private ProviderCache providerCache;

  private Registry registry;
  private NodeCachingAgent agent;

  @BeforeEach
  void setUp() {
    registry = mockRegistry();
    when(credentials.getName()).thenReturn(ACCOUNT_NAME);
    when(credentials.getCredentials()).thenReturn(api);
    agent = new NodeCachingAgent(credentials, registry, new ProxmoxTagNamer());
  }

  @Test
  void loadDataReturnsBothNodesWhenApiSucceeds() throws Exception {
    ProxmoxNode node1 = ProxmoxNode.builder().node(NODE_NAME).status("online").build();
    ProxmoxNode node2 = ProxmoxNode.builder().node("pve02").status("online").build();

    mockGetNodesResponse(List.of(node1, node2));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.NODE.name());
    assertThat(cached).hasSize(2);

    List<String> ids = cached.stream().map(CacheData::getId).toList();
    assertThat(ids)
        .containsExactlyInAnyOrder(
            ProxmoxCacheKeys.node(ACCOUNT_NAME, NODE_NAME),
            ProxmoxCacheKeys.node(ACCOUNT_NAME, "pve02"));
  }

  @Test
  void loadDataIncludesNodeAttributesInCache() throws Exception {
    ProxmoxNode node = ProxmoxNode.builder().node(NODE_NAME).status("online").maxCpu(8).build();
    mockGetNodesResponse(List.of(node));

    CacheResult result = agent.loadData(providerCache);

    CacheData cached =
        result.getCacheResults().get(ProxmoxResourceType.NODE.name()).iterator().next();
    assertThat(cached.getAttributes().get("node")).isEqualTo(NODE_NAME);
    assertThat(cached.getAttributes().get("status")).isEqualTo("online");
  }

  @Test
  void loadDataReturnsEmptyWhenResponseIsUnsuccessful() throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    when(api.getNodes()).thenReturn(call);
    when(call.execute())
        .thenReturn(Response.error(500, ResponseBody.create(null, "Internal Server Error")));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.NODE.name());
    assertThat(cached).isEmpty();
  }

  @Test
  void loadDataReturnsEmptyWhenIoExceptionThrown() throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenThrow(new IOException("connection refused"));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.NODE.name());
    assertThat(cached).isEmpty();
  }

  @Test
  void loadDataSkipsNodesWithNullNodeName() throws Exception {
    ProxmoxNode validNode = ProxmoxNode.builder().node(NODE_NAME).build();
    ProxmoxNode nullNameNode = ProxmoxNode.builder().node(null).build();
    mockGetNodesResponse(List.of(validNode, nullNameNode));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.NODE.name());
    assertThat(cached).hasSize(1);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void mockGetNodesResponse(List<ProxmoxNode> nodes) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxNode>> body = new ProxmoxResponse<>();
    body.setData(nodes);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
  }

  private static Registry mockRegistry() {
    Registry reg = mock(Registry.class);
    Timer timer = mock(Timer.class);
    Counter counter = mock(Counter.class);
    when(reg.timer(anyString(), any(String[].class))).thenReturn(timer);
    when(reg.counter(anyString(), any(String[].class))).thenReturn(counter);
    return reg;
  }
}
