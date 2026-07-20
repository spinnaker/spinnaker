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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.proxmox;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProxmoxServerGroupCreator implements ServerGroupCreator {

  @Override
  public boolean isKatoResultExpected() {
    return false;
  }

  @Override
  public String getCloudProvider() {
    return "proxmox";
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return Optional.of("Proxmox");
  }

  @Override
  public List<Map> getOperations(StageExecution stage) {
    Map<String, Object> operation = new HashMap<>();

    // Deploy (pipeline) stages nest the deploy configuration under "cluster"; ad hoc
    // createServerGroup orchestrations supply it directly in the stage context.
    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    // Deck's deploy stage strips "credentials" from stored clusters, leaving only "account";
    // clouddriver's proxmox converters resolve credentials from the "credentials" key.
    operation.computeIfAbsent("credentials", (k) -> operation.get("account"));

    List<Map> operations = new ArrayList<>();
    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put(OPERATION, operation);
    operations.add(wrapper);
    return operations;
  }
}
