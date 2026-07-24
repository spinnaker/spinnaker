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

    bindTemplateFromBakeResults(stage, operation);

    List<Map> operations = new ArrayList<>();
    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put(OPERATION, operation);
    operations.add(wrapper);
    return operations;
  }

  /**
   * When the deploy configuration has no template, bind the template VMID produced by an upstream
   * Bake stage: the proxmox bake handler scrapes the new template's VMID into the bake result, and
   * orca propagates it through {@code deploymentDetails[].imageId}.
   */
  private void bindTemplateFromBakeResults(StageExecution stage, Map<String, Object> operation) {
    Object existing = operation.get("templateVmid");
    boolean hasTemplate =
        existing != null && !existing.toString().isBlank() && !"0".equals(existing.toString());
    if (hasTemplate) {
      return;
    }

    Object detailsObj = stage.getContext().get("deploymentDetails");
    if (!(detailsObj instanceof List)) {
      return;
    }
    Object region = operation.get("region");
    Map<String, Object> fallback = null;
    for (Object entry : (List<?>) detailsObj) {
      if (!(entry instanceof Map)) continue;
      Map<String, Object> detail = (Map<String, Object>) entry;
      Object provider = detail.get("cloudProvider");
      if (provider != null && !"proxmox".equals(provider)) continue;
      Object imageId = detail.getOrDefault("imageId", detail.get("ami"));
      if (imageId == null || !imageId.toString().matches("\\d+")) continue;
      if (region != null && region.equals(detail.get("region"))) {
        applyTemplate(operation, imageId, detail);
        return;
      }
      if (fallback == null) {
        fallback = detail;
      }
    }
    if (fallback != null) {
      applyTemplate(operation, fallback.getOrDefault("imageId", fallback.get("ami")), fallback);
    }
  }

  private void applyTemplate(
      Map<String, Object> operation, Object imageId, Map<String, Object> detail) {
    operation.put("templateVmid", Integer.parseInt(imageId.toString()));
    // The baked template lives on the node the bake ran on, which may differ from the target.
    Object bakeRegion = detail.get("region");
    if (bakeRegion != null && operation.get("templateNode") == null) {
      operation.put("templateNode", bakeRegion);
    }
  }
}
