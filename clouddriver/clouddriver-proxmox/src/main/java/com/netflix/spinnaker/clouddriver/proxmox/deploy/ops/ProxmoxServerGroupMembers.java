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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.ops;

import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import retrofit2.Call;

/**
 * Resolves the live member VMs/containers of a Spinnaker server group by scanning the cluster and
 * matching the {@code spinnaker-server-group} tag (falling back to the resource name, mirroring
 * {@code ProxmoxServerClusterProvider}).
 */
public final class ProxmoxServerGroupMembers {

  /** A server group member: one QEMU VM or LXC container. */
  public record Member(
      String node, int vmid, String vmType, String name, String tags, String status) {}

  private ProxmoxServerGroupMembers() {}

  /**
   * Finds all members of {@code serverGroupName}, optionally restricted to a node ({@code region};
   * null scans all nodes). Members are sorted by vmid so scale-down removes the newest first.
   */
  public static List<Member> resolve(ProxmoxApiService api, String serverGroupName, String region) {
    List<Member> members = new ArrayList<>();
    for (ProxmoxNode node : execute(api.getNodes())) {
      if (region != null && !region.equals(node.getNode())) {
        continue;
      }
      for (ProxmoxVm vm : execute(api.getVms(node.getNode()))) {
        if (vm.getVmId() == null || Objects.equals(1, vm.getTemplate())) continue;
        if (belongsTo(serverGroupName, vm.getTags(), vm.getName())) {
          members.add(
              new Member(
                  node.getNode(),
                  vm.getVmId(),
                  "qemu",
                  vm.getName(),
                  vm.getTags(),
                  vm.getStatus()));
        }
      }
      for (ProxmoxLxc lxc : execute(api.getContainers(node.getNode()))) {
        if (lxc.getVmId() == null || Objects.equals(1, lxc.getTemplate())) continue;
        if (belongsTo(serverGroupName, lxc.getTags(), lxc.getName())) {
          members.add(
              new Member(
                  node.getNode(),
                  lxc.getVmId(),
                  "lxc",
                  lxc.getName(),
                  lxc.getTags(),
                  lxc.getStatus()));
        }
      }
    }
    members.sort(Comparator.comparingInt(Member::vmid));
    return members;
  }

  /**
   * Finds specific instances by name (Spinnaker instance ids are Proxmox VM/container names),
   * optionally restricted to a node.
   */
  public static List<Member> findByNames(
      ProxmoxApiService api, java.util.Collection<String> names, String region) {
    java.util.Set<String> wanted = new java.util.HashSet<>(names);
    List<Member> found = new ArrayList<>();
    for (ProxmoxNode node : execute(api.getNodes())) {
      if (region != null && !region.equals(node.getNode())) {
        continue;
      }
      for (ProxmoxVm vm : execute(api.getVms(node.getNode()))) {
        if (vm.getVmId() == null || Objects.equals(1, vm.getTemplate())) continue;
        if (wanted.contains(vm.getName())) {
          found.add(
              new Member(
                  node.getNode(),
                  vm.getVmId(),
                  "qemu",
                  vm.getName(),
                  vm.getTags(),
                  vm.getStatus()));
        }
      }
      for (ProxmoxLxc lxc : execute(api.getContainers(node.getNode()))) {
        if (lxc.getVmId() == null || Objects.equals(1, lxc.getTemplate())) continue;
        if (wanted.contains(lxc.getName())) {
          found.add(
              new Member(
                  node.getNode(),
                  lxc.getVmId(),
                  "lxc",
                  lxc.getName(),
                  lxc.getTags(),
                  lxc.getStatus()));
        }
      }
    }
    found.sort(Comparator.comparingInt(Member::vmid));
    return found;
  }

  /** Locates the node hosting the given template VMID (templates are excluded from members). */
  public static String findTemplateNode(ProxmoxApiService api, int templateVmid, String vmType) {
    for (ProxmoxNode node : execute(api.getNodes())) {
      if ("lxc".equals(vmType)) {
        for (ProxmoxLxc lxc : execute(api.getContainers(node.getNode()))) {
          if (Objects.equals(templateVmid, lxc.getVmId())) return node.getNode();
        }
      } else {
        for (ProxmoxVm vm : execute(api.getVms(node.getNode()))) {
          if (Objects.equals(templateVmid, vm.getVmId())) return node.getNode();
        }
      }
    }
    return null;
  }

  private static boolean belongsTo(String serverGroupName, String tags, String name) {
    Map<String, String> tagMap = ProxmoxTagNamer.parseTags(tags);
    return serverGroupName.equals(tagMap.getOrDefault(ProxmoxTagNamer.SERVER_GROUP_TAG, name));
  }

  private static final java.util.regex.Pattern VERSION_SUFFIX =
      java.util.regex.Pattern.compile("-v(\\d+)$");

  /**
   * Determines the next server group version for a cluster by scanning existing members' sequence
   * tags and {@code -vNNN} name suffixes. Returns 0 for a brand new cluster; unversioned legacy
   * members count as sequence 0.
   */
  public static int nextSequence(ProxmoxApiService api, String clusterName) {
    int max = -1;
    for (ProxmoxNode node : execute(api.getNodes())) {
      List<ProxmoxResourceView> resources = new ArrayList<>();
      for (ProxmoxVm vm : execute(api.getVms(node.getNode()))) {
        if (vm.getVmId() != null && !Objects.equals(1, vm.getTemplate())) {
          resources.add(new ProxmoxResourceView(vm.getName(), vm.getTags()));
        }
      }
      for (ProxmoxLxc lxc : execute(api.getContainers(node.getNode()))) {
        if (lxc.getVmId() != null && !Objects.equals(1, lxc.getTemplate())) {
          resources.add(new ProxmoxResourceView(lxc.getName(), lxc.getTags()));
        }
      }
      for (ProxmoxResourceView resource : resources) {
        Map<String, String> tagMap = ProxmoxTagNamer.parseTags(resource.tags());
        String sg = tagMap.getOrDefault(ProxmoxTagNamer.SERVER_GROUP_TAG, resource.name());
        boolean inCluster =
            clusterName.equals(tagMap.get(ProxmoxTagNamer.CLUSTER_TAG))
                || (sg != null && (sg.equals(clusterName) || sg.startsWith(clusterName + "-v")));
        if (!inCluster) {
          continue;
        }
        int sequence = 0;
        String sequenceTag = tagMap.get(ProxmoxTagNamer.SEQUENCE_TAG);
        if (sequenceTag != null) {
          try {
            sequence = Integer.parseInt(sequenceTag);
          } catch (NumberFormatException ignored) {
          }
        } else if (sg != null) {
          java.util.regex.Matcher matcher = VERSION_SUFFIX.matcher(sg);
          if (matcher.find()) {
            sequence = Integer.parseInt(matcher.group(1));
          }
        }
        max = Math.max(max, sequence);
      }
    }
    return max + 1;
  }

  private record ProxmoxResourceView(String name, String tags) {}

  private static <T> List<T> execute(Call<ProxmoxResponse<List<T>>> call) {
    try {
      retrofit2.Response<ProxmoxResponse<List<T>>> response = call.execute();
      if (!response.isSuccessful()
          || response.body() == null
          || response.body().getData() == null) {
        return List.of();
      }
      return response.body().getData();
    } catch (IOException e) {
      throw new RuntimeException("Failed to query Proxmox while resolving server group members", e);
    }
  }
}
