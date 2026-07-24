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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProxmoxInstanceProvider implements InstanceProvider<ProxmoxInstance, String> {

  private final ProxmoxServerClusterProvider clusterProvider;

  @Override
  public String getCloudProvider() {
    return ProxmoxProvider.ID;
  }

  /**
   * Finds a Proxmox instance by scanning the server groups for the given account. The {@code
   * region} param is a Proxmox node name; {@code id} is the VM/container name used as the instance
   * id.
   */
  @Override
  public ProxmoxInstance getInstance(String account, String region, String id) {
    return clusterProvider.getClusters(null, account).stream()
        .flatMap(c -> c.getServerGroups().stream())
        .filter(sg -> region == null || region.equals(sg.getRegion()))
        .flatMap(sg -> sg.getInstances().stream())
        .filter(i -> i instanceof ProxmoxInstance)
        .map(i -> (ProxmoxInstance) i)
        .filter(i -> id.equals(i.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }
}
