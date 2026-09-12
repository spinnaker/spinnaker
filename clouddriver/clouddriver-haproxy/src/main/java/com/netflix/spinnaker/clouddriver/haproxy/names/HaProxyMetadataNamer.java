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
package com.netflix.spinnaker.clouddriver.haproxy.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.MonikerHelper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives Spinnaker {@link Moniker} values from HAProxy Data Plane API {@code metadata} maps.
 *
 * <p>Looks for {@code spinnaker-*} metadata keys (e.g. {@code spinnaker-app}) on frontends and
 * backends, falling back to Frigga parsing of the section name when metadata is absent.
 */
@Component
public class HaProxyMetadataNamer implements NamingStrategy<HaProxyResource> {

  public static final String APP_KEY = "spinnaker-app";
  public static final String CLUSTER_KEY = "spinnaker-cluster";
  public static final String STACK_KEY = "spinnaker-stack";
  public static final String DETAIL_KEY = "spinnaker-detail";
  public static final String SEQUENCE_KEY = "spinnaker-sequence";

  @Override
  public String getName() {
    return "haProxyMetadata";
  }

  @Override
  public Moniker deriveMoniker(HaProxyResource resource) {
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

    Map<String, Object> metadata = resource.getMetadata();
    if (metadata != null && !metadata.isEmpty()) {
      String app = asString(metadata.get(APP_KEY));
      String cluster = asString(metadata.get(CLUSTER_KEY));
      String stack = asString(metadata.get(STACK_KEY));
      String detail = asString(metadata.get(DETAIL_KEY));
      String sequence = asString(metadata.get(SEQUENCE_KEY));

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
  public void applyMoniker(HaProxyResource resource, Moniker moniker) {
    // Read-only until write operations land; metadata mutation not supported.
  }

  private static String asString(Object value) {
    return value != null ? value.toString() : null;
  }
}
