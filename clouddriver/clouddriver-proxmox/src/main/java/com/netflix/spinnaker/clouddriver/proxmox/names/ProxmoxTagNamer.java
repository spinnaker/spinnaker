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
package com.netflix.spinnaker.clouddriver.proxmox.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.MonikerHelper;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Derives Spinnaker {@link Moniker} values from Proxmox 8-style resource tags.
 *
 * <p>Proxmox tags use the {@code category:value} format, separated by semicolons. This namer looks
 * for tags prefixed with {@code spinnaker-} (e.g. {@code spinnaker-app:myapp}) and falls back to
 * Frigga name parsing when tags are absent.
 */
@Component
public class ProxmoxTagNamer implements NamingStrategy<ProxmoxResource> {

  public static final String APP_TAG = "spinnaker-app";
  public static final String CLUSTER_TAG = "spinnaker-cluster";
  public static final String STACK_TAG = "spinnaker-stack";
  public static final String DETAIL_TAG = "spinnaker-detail";
  public static final String SEQUENCE_TAG = "spinnaker-sequence";

  @Override
  public String getName() {
    return "proxmoxTags";
  }

  @Override
  public Moniker deriveMoniker(ProxmoxResource resource) {
    String name = resource.getName();
    Names parsed = Names.parseName(name != null ? name : "");

    Moniker moniker =
        Moniker.builder()
            .app(parsed.getApp())
            .cluster(parsed.getCluster())
            .detail(parsed.getDetail())
            .stack(parsed.getStack())
            .sequence(parsed.getSequence())
            .build();

    Map<String, String> tags = parseTags(resource.getTags());
    if (!tags.isEmpty()) {
      String app = tags.get(APP_TAG);
      String cluster = tags.get(CLUSTER_TAG);
      String stack = tags.get(STACK_TAG);
      String detail = tags.get(DETAIL_TAG);
      String sequence = tags.get(SEQUENCE_TAG);

      if (app != null) moniker.setApp(app);
      if (stack != null) moniker.setStack(stack);
      if (detail != null) moniker.setDetail(detail);
      if (cluster == null && (moniker.getStack() != null || moniker.getDetail() != null)) {
        cluster =
            MonikerHelper.getClusterName(moniker.getApp(), moniker.getStack(), moniker.getDetail());
      }
      if (cluster != null) moniker.setCluster(cluster);
      if (sequence != null) {
        try {
          moniker.setSequence(Integer.parseInt(sequence));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    return moniker;
  }

  @Override
  public void applyMoniker(ProxmoxResource resource, Moniker moniker) {
    // Read-only provider; tag mutation not supported
  }

  static Map<String, String> parseTags(String rawTags) {
    if (rawTags == null || rawTags.isBlank()) {
      return Map.of();
    }
    return Arrays.stream(rawTags.split(";"))
        .filter(t -> t.contains(":"))
        .map(t -> t.split(":", 2))
        .filter(parts -> parts.length == 2 && !parts[0].isBlank())
        .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim(), (a, b) -> a));
  }
}
