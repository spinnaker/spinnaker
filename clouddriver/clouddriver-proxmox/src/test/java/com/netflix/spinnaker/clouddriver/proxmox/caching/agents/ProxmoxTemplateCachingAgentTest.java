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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
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
class ProxmoxTemplateCachingAgentTest {

  private static final String ACCOUNT = "my-proxmox";
  private static final String NODE = "pve01";

  @Mock private ProxmoxNamedAccountCredentials credentials;
  @Mock private ProxmoxApiService api;
  @Mock private ProviderCache providerCache;

  private ProxmoxTemplateCachingAgent agent;

  @BeforeEach
  void setUp() {
    lenient().when(credentials.getName()).thenReturn(ACCOUNT);
    lenient().when(credentials.getCredentials()).thenReturn(api);
    agent = new ProxmoxTemplateCachingAgent(credentials);
  }

  @Test
  void loadDataCachesVmTemplateAsImage() throws Exception {
    mockNodes(List.of(ProxmoxNode.builder().node(NODE).build()));
    ProxmoxVm template = ProxmoxVm.builder().vmId(9000).name("ubuntu-template").template(1).build();
    ProxmoxVm regular = ProxmoxVm.builder().vmId(101).name("myapp-v001").build();
    mockVms(NODE, List.of(template, regular));
    mockContainers(NODE, List.of());

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).hasSize(1);
    CacheData image = images.iterator().next();
    assertThat(image.getId()).isEqualTo(ProxmoxCacheKeys.image(ACCOUNT, NODE, 9000));
    assertThat(image.getAttributes().get("name")).isEqualTo("ubuntu-template");
    assertThat(image.getAttributes().get("vmType")).isEqualTo("qemu");
    assertThat(image.getAttributes().get("region")).isEqualTo(NODE);
    assertThat(image.getAttributes().get("account")).isEqualTo(ACCOUNT);
  }

  @Test
  void loadDataCachesLxcTemplateAsImage() throws Exception {
    mockNodes(List.of(ProxmoxNode.builder().node(NODE).build()));
    mockVms(NODE, List.of());
    ProxmoxLxc template =
        ProxmoxLxc.builder().vmId(9001).name("debian-template").template(1).build();
    ProxmoxLxc regular = ProxmoxLxc.builder().vmId(200).name("web-v001").build();
    mockContainers(NODE, List.of(template, regular));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).hasSize(1);
    CacheData image = images.iterator().next();
    assertThat(image.getId()).isEqualTo(ProxmoxCacheKeys.image(ACCOUNT, NODE, 9001));
    assertThat(image.getAttributes().get("name")).isEqualTo("debian-template");
    assertThat(image.getAttributes().get("vmType")).isEqualTo("lxc");
  }

  @Test
  void loadDataExcludesNonTemplateVmsAndContainers() throws Exception {
    mockNodes(List.of(ProxmoxNode.builder().node(NODE).build()));
    mockVms(NODE, List.of(ProxmoxVm.builder().vmId(101).name("myapp-v001").build()));
    mockContainers(NODE, List.of(ProxmoxLxc.builder().vmId(200).name("web-v001").build()));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).isEmpty();
  }

  @Test
  void loadDataReturnsEmptyWhenNodeFetchFails() throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenThrow(new IOException("network failure"));

    CacheResult result = agent.loadData(providerCache);

    assertThat(result.getCacheResults()).isEmpty();
  }

  @Test
  void loadDataCachesBothVmAndLxcTemplates() throws Exception {
    mockNodes(List.of(ProxmoxNode.builder().node(NODE).build()));
    mockVms(
        NODE, List.of(ProxmoxVm.builder().vmId(9000).name("ubuntu-template").template(1).build()));
    mockContainers(
        NODE, List.of(ProxmoxLxc.builder().vmId(9001).name("debian-template").template(1).build()));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).hasSize(2);
  }

  @Test
  void loadDataSkipsNodeWhenVmFetchFails() throws Exception {
    ProxmoxNode goodNode = ProxmoxNode.builder().node(NODE).build();
    ProxmoxNode badNode = ProxmoxNode.builder().node("pve02").build();
    mockNodes(List.of(goodNode, badNode));

    mockVms(NODE, List.of(ProxmoxVm.builder().vmId(9000).name("tmpl").template(1).build()));
    mockContainers(NODE, List.of());

    Call<ProxmoxResponse<List<ProxmoxVm>>> failCall = mock(Call.class);
    when(api.getVms("pve02")).thenReturn(failCall);
    when(failCall.execute()).thenThrow(new IOException("pve02 down"));

    Call<ProxmoxResponse<List<ProxmoxLxc>>> failLxcCall = mock(Call.class);
    when(api.getContainers("pve02")).thenReturn(failLxcCall);
    when(failLxcCall.execute()).thenThrow(new IOException("pve02 down"));

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).hasSize(1);
    assertThat(images.iterator().next().getId())
        .isEqualTo(ProxmoxCacheKeys.image(ACCOUNT, NODE, 9000));
  }

  @Test
  void loadDataSkipsNodeWhenVmResponseIsUnsuccessful() throws Exception {
    mockNodes(List.of(ProxmoxNode.builder().node(NODE).build()));

    Call<ProxmoxResponse<List<ProxmoxVm>>> call = mock(Call.class);
    when(api.getVms(NODE)).thenReturn(call);
    when(call.execute()).thenReturn(Response.error(403, ResponseBody.create(null, "Forbidden")));

    mockContainers(NODE, List.of());

    CacheResult result = agent.loadData(providerCache);

    Collection<CacheData> images = result.getCacheResults().get(ProxmoxResourceType.IMAGE.name());
    assertThat(images).isEmpty();
  }

  @Test
  void getCacheKeyPatternsReturnsImageGlob() {
    assertThat(agent.getCacheKeyPatterns())
        .isPresent()
        .hasValueSatisfying(
            m ->
                assertThat(m)
                    .containsEntry(
                        ProxmoxResourceType.IMAGE.name(),
                        ProxmoxCacheKeys.glob(ProxmoxResourceType.IMAGE.name(), ACCOUNT)));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void mockNodes(List<ProxmoxNode> nodes) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxNode>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxNode>> body = new ProxmoxResponse<>();
    body.setData(nodes);
    when(api.getNodes()).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
  }

  private void mockVms(String node, List<ProxmoxVm> vms) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxVm>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxVm>> body = new ProxmoxResponse<>();
    body.setData(vms);
    when(api.getVms(node)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
  }

  private void mockContainers(String node, List<ProxmoxLxc> containers) throws Exception {
    Call<ProxmoxResponse<List<ProxmoxLxc>>> call = mock(Call.class);
    ProxmoxResponse<List<ProxmoxLxc>> body = new ProxmoxResponse<>();
    body.setData(containers);
    when(api.getContainers(node)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
  }
}
