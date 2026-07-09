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
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
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
class VMCachingAgentTest {

  private static final String ACCOUNT_NAME = "my-proxmox";
  private static final String NODE_NAME = "pve01";

  @Mock private ProxmoxNamedAccountCredentials credentials;
  @Mock private ProxmoxApiService api;
  @Mock private ProviderCache providerCache;

  private Registry registry;
  private VMCachingAgent agent;

  @BeforeEach
  void setUp() {
    registry = mockRegistry();
    when(credentials.getName()).thenReturn(ACCOUNT_NAME);
    when(credentials.getCredentials()).thenReturn(api);
    agent = new VMCachingAgent(credentials, registry, new ProxmoxTagNamer());
  }

  @Test
  void loadDataReturnsTwoVmsForOneNode() throws Exception {
    mockGetNodesResponse(List.of(ProxmoxNode.builder().node(NODE_NAME).build()));

    ProxmoxVm vm1 = ProxmoxVm.builder().vmId(101).name("myapp-prod-v001").build();
    ProxmoxVm vm2 = ProxmoxVm.builder().vmId(102).name("myapp-prod-v002").build();
    mockGetVmsResponse(NODE_NAME, List.of(vm1, vm2));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    assertThat(cached).hasSize(2);

    List<String> ids = cached.stream().map(CacheData::getId).toList();
    assertThat(ids)
        .containsExactlyInAnyOrder(
            ProxmoxCacheKeys.vm(ACCOUNT_NAME, NODE_NAME, 101),
            ProxmoxCacheKeys.vm(ACCOUNT_NAME, NODE_NAME, 102));
  }

  @Test
  void loadDataSetsNodeFieldOnVm() throws Exception {
    mockGetNodesResponse(List.of(ProxmoxNode.builder().node(NODE_NAME).build()));
    ProxmoxVm vm = ProxmoxVm.builder().vmId(101).name("myapp-v001").build();
    mockGetVmsResponse(NODE_NAME, List.of(vm));

    CacheResult result = agent.loadData(providerCache);

    CacheData cached =
        result.getCacheResults().get(ProxmoxResourceType.VM.name()).iterator().next();
    assertThat(cached.getAttributes().get("node")).isEqualTo(NODE_NAME);
  }

  @Test
  void loadDataSkipsVmWithNullVmId() throws Exception {
    mockGetNodesResponse(List.of(ProxmoxNode.builder().node(NODE_NAME).build()));
    ProxmoxVm valid = ProxmoxVm.builder().vmId(101).name("ok-vm").build();
    ProxmoxVm nullId = ProxmoxVm.builder().vmId(null).name("no-id-vm").build();
    mockGetVmsResponse(NODE_NAME, List.of(valid, nullId));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    assertThat(cached).hasSize(1);
  }

  @Test
  void loadDataReturnsEmptyWhenNodeFetchFails() throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenThrow(new IOException("node fetch failed"));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    assertThat(cached).isEmpty();
  }

  @Test
  void loadDataSkipsNodeWhenVmFetchFails() throws Exception {
    ProxmoxNode goodNode = ProxmoxNode.builder().node(NODE_NAME).build();
    ProxmoxNode badNode = ProxmoxNode.builder().node("pve02").build();
    mockGetNodesResponse(List.of(goodNode, badNode));

    ProxmoxVm vm = ProxmoxVm.builder().vmId(101).name("myapp-v001").build();
    mockGetVmsResponse(NODE_NAME, List.of(vm));

    Call<ProxmoxResponse<List<ProxmoxVm>>> failCall = mock(Call.class);
    when(api.getVms("pve02")).thenReturn(failCall);
    when(failCall.execute()).thenThrow(new IOException("pve02 down"));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    // only the vm from pve01 should be present
    assertThat(cached).hasSize(1);
    assertThat(cached.iterator().next().getId())
        .isEqualTo(ProxmoxCacheKeys.vm(ACCOUNT_NAME, NODE_NAME, 101));
  }

  @Test
  void loadDataSkipsVmTemplates() throws Exception {
    mockGetNodesResponse(List.of(ProxmoxNode.builder().node(NODE_NAME).build()));
    ProxmoxVm regular = ProxmoxVm.builder().vmId(101).name("myapp-v001").build();
    ProxmoxVm template = ProxmoxVm.builder().vmId(9000).name("ubuntu-template").template(1).build();
    mockGetVmsResponse(NODE_NAME, List.of(regular, template));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    assertThat(cached).hasSize(1);
    assertThat(cached.iterator().next().getId())
        .isEqualTo(ProxmoxCacheKeys.vm(ACCOUNT_NAME, NODE_NAME, 101));
  }

  @Test
  void loadDataReturnsEmptyWhenVmResponseIsUnsuccessful() throws Exception {
    mockGetNodesResponse(List.of(ProxmoxNode.builder().node(NODE_NAME).build()));

    Call<ProxmoxResponse<List<ProxmoxVm>>> call = mock(Call.class);
    when(api.getVms(NODE_NAME)).thenReturn(call);
    when(call.execute()).thenReturn(Response.error(403, ResponseBody.create(null, "Forbidden")));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> cached = result.getCacheResults().get(ProxmoxResourceType.VM.name());
    assertThat(cached).isEmpty();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void mockGetNodesResponse(List<ProxmoxNode> nodes) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxNode>> body = new ProxmoxResponse<>();
    body.setData(nodes);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
  }

  private void mockGetVmsResponse(String node, List<ProxmoxVm> vms) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxVm>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxVm>> body = new ProxmoxResponse<>();
    body.setData(vms);
    when(api.getVms(node)).thenReturn(call);
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
