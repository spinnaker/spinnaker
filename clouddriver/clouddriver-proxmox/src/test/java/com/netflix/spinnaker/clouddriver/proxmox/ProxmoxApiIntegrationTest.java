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
package com.netflix.spinnaker.clouddriver.proxmox;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxStorage;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import com.netflix.spinnaker.config.ProxmoxConfigurationProperties;
import java.util.List;
import org.junit.jupiter.api.*;
import retrofit2.Response;

@Tag("integration")
@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxmoxApiIntegrationTest {

  private static final String SERVER = ""; // <-- replace as needed
  private static final int PORT = 8006;
  private static final String USERNAME = "root@pam";
  private static final String PASSWORD = ""; // <-- fill in before running

  private ProxmoxApiService api;

  @BeforeAll
  void setup() {
    ProxmoxConfigurationProperties.ProxmoxManagedAccount account =
        new ProxmoxConfigurationProperties.ProxmoxManagedAccount();
    account.setName("integration-test");
    account.setServer(SERVER);
    account.setPort(PORT);
    account.setUserName(USERNAME);
    account.setPassword(PASSWORD);
    account.setInsecure(true);
    api = new ProxmoxNamedAccountCredentials(account).getCredentials();
  }

  @Test
  void nodesAreLoaded() throws Exception {
    Response<ProxmoxResponse<List<ProxmoxNode>>> response = api.getNodes().execute();

    assertThat(response.isSuccessful()).isTrue();
    List<ProxmoxNode> nodes = response.body().getData();
    assertThat(nodes).isNotNull().isNotEmpty();

    System.out.println("=== Nodes ===");
    nodes.forEach(
        n ->
            System.out.printf(
                "  node=%-20s status=%-10s cpu=%.1f%%%n",
                n.getNode(), n.getStatus(), n.getCpu() != null ? n.getCpu() * 100 : 0.0));
  }

  @Test
  void vmsAreLoadedForEachNode() throws Exception {
    List<ProxmoxNode> nodes = loadNodes();

    System.out.println("=== VMs ===");
    for (ProxmoxNode node : nodes) {
      Response<ProxmoxResponse<List<ProxmoxVm>>> response = api.getVms(node.getNode()).execute();

      assertThat(response.isSuccessful())
          .as("GET /nodes/%s/qemu returned HTTP %d", node.getNode(), response.code())
          .isTrue();
      List<ProxmoxVm> vms = response.body().getData();
      assertThat(vms).isNotNull();

      System.out.printf("  node=%-20s vms=%d%n", node.getNode(), vms.size());
      vms.forEach(
          vm ->
              System.out.printf(
                  "    vmid=%-6d name=%-30s status=%s%n",
                  vm.getVmId(), vm.getName(), vm.getStatus()));
    }
  }

  @Test
  void containersAreLoadedForEachNode() throws Exception {
    List<ProxmoxNode> nodes = loadNodes();

    System.out.println("=== Containers (LXC) ===");
    for (ProxmoxNode node : nodes) {
      Response<ProxmoxResponse<List<ProxmoxLxc>>> response =
          api.getContainers(node.getNode()).execute();

      assertThat(response.isSuccessful())
          .as("GET /nodes/%s/lxc returned HTTP %d", node.getNode(), response.code())
          .isTrue();
      List<ProxmoxLxc> containers = response.body().getData();
      assertThat(containers).isNotNull();

      System.out.printf("  node=%-20s containers=%d%n", node.getNode(), containers.size());
      containers.forEach(
          c ->
              System.out.printf(
                  "    vmid=%-6d name=%-30s status=%s%n", c.getVmId(), c.getName(), c.getStatus()));
    }
  }

  @Test
  void storageIsLoadedForEachNode() throws Exception {
    List<ProxmoxNode> nodes = loadNodes();

    System.out.println("=== Storage ===");
    for (ProxmoxNode node : nodes) {
      Response<ProxmoxResponse<List<ProxmoxStorage>>> response =
          api.getStorage(node.getNode()).execute();

      assertThat(response.isSuccessful())
          .as("GET /nodes/%s/storage returned HTTP %d", node.getNode(), response.code())
          .isTrue();
      List<ProxmoxStorage> storages = response.body().getData();
      assertThat(storages).isNotNull().isNotEmpty();

      System.out.printf("  node=%-20s storage_pools=%d%n", node.getNode(), storages.size());
      storages.forEach(
          s ->
              System.out.printf(
                  "    storage=%-20s type=%-12s avail=%s / total=%s%n",
                  s.getStorage(),
                  s.getPluginType(),
                  formatBytes(s.getAvail()),
                  formatBytes(s.getTotal())));
    }
  }

  private List<ProxmoxNode> loadNodes() throws Exception {
    Response<ProxmoxResponse<List<ProxmoxNode>>> response = api.getNodes().execute();
    assertThat(response.isSuccessful()).isTrue();
    return response.body().getData();
  }

  private static String formatBytes(Long bytes) {
    if (bytes == null) return "?";
    if (bytes >= 1L << 30) return String.format("%.1fG", bytes / (double) (1L << 30));
    if (bytes >= 1L << 20) return String.format("%.1fM", bytes / (double) (1L << 20));
    return bytes + "B";
  }
}
