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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HaProxyMetadataNamerTest {

  private final HaProxyMetadataNamer namer = new HaProxyMetadataNamer();

  private static HaProxyResource resource(String name, Map<String, Object> metadata) {
    return new HaProxyResource() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Map<String, Object> getMetadata() {
        return metadata;
      }
    };
  }

  @Test
  void metadataDrivesTheMoniker() {
    Moniker moniker =
        namer.deriveMoniker(
            resource(
                "edge_proxy",
                Map.of(
                    "spinnaker-app", "web",
                    "spinnaker-stack", "prod",
                    "spinnaker-detail", "edge")));

    assertThat(moniker.getApp()).isEqualTo("web");
    assertThat(moniker.getStack()).isEqualTo("prod");
    assertThat(moniker.getDetail()).isEqualTo("edge");
    assertThat(moniker.getCluster()).isEqualTo("web-prod-edge");
  }

  @Test
  void explicitClusterMetadataWins() {
    Moniker moniker =
        namer.deriveMoniker(
            resource(
                "edge_proxy",
                Map.of(
                    "spinnaker-app", "web",
                    "spinnaker-stack", "prod",
                    "spinnaker-cluster", "web-custom")));

    assertThat(moniker.getCluster()).isEqualTo("web-custom");
  }

  @Test
  void fallsBackToFriggaNameParsing() {
    Moniker moniker = namer.deriveMoniker(resource("web-main-v001", Map.of()));

    assertThat(moniker.getApp()).isEqualTo("web");
    assertThat(moniker.getStack()).isEqualTo("main");
    assertThat(moniker.getCluster()).isEqualTo("web-main");
    assertThat(moniker.getSequence()).isEqualTo(1);
  }

  @Test
  void metadataOverridesFriggaParsing() {
    Moniker moniker =
        namer.deriveMoniker(resource("web-main-v001", Map.of("spinnaker-app", "storefront")));

    assertThat(moniker.getApp()).isEqualTo("storefront");
  }

  @Test
  void sequenceMetadataIsParsedAndBadValuesIgnored() {
    assertThat(
            namer
                .deriveMoniker(
                    resource("api", Map.of("spinnaker-app", "api", "spinnaker-sequence", "3")))
                .getSequence())
        .isEqualTo(3);
    assertThat(
            namer
                .deriveMoniker(
                    resource("api", Map.of("spinnaker-app", "api", "spinnaker-sequence", "banana")))
                .getSequence())
        .isNull();
  }

  @Test
  void nullMetadataAndUnparseableNamesAreTolerated() {
    Moniker moniker = namer.deriveMoniker(resource("UPPER_case_name", null));

    assertThat(moniker).isNotNull();
  }
}
