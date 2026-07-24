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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxmoxTagNamerTest {

  private ProxmoxTagNamer namer;

  @BeforeEach
  void setUp() {
    namer = new ProxmoxTagNamer();
  }

  // ── parseTags ──────────────────────────────────────────────────────────────

  @Test
  void parseTagsParsesSimplePairs() {
    Map<String, String> tags =
        ProxmoxTagNamer.parseTags("spinnaker-app+myapp;spinnaker-stack+prod");
    assertThat(tags)
        .containsEntry("spinnaker-app", "myapp")
        .containsEntry("spinnaker-stack", "prod");
  }

  @Test
  void parseTagsReturnsEmptyMapForNull() {
    assertThat(ProxmoxTagNamer.parseTags(null)).isEmpty();
  }

  @Test
  void parseTagsReturnsEmptyMapForBlank() {
    assertThat(ProxmoxTagNamer.parseTags("   ")).isEmpty();
  }

  @Test
  void parseTagsSkipsEntriesWithoutPlus() {
    Map<String, String> tags = ProxmoxTagNamer.parseTags("notavalid;spinnaker-app+myapp");
    assertThat(tags).hasSize(1).containsKey("spinnaker-app");
  }

  @Test
  void parseTagsHandlesSingleEntry() {
    Map<String, String> tags = ProxmoxTagNamer.parseTags("spinnaker-app+myapp");
    assertThat(tags).containsEntry("spinnaker-app", "myapp");
  }

  @Test
  void parseTagsHandlesBlankEntry() {
    // Leading semicolon produces a blank segment that should be skipped
    Map<String, String> tags = ProxmoxTagNamer.parseTags(";spinnaker-app+myapp");
    assertThat(tags).containsEntry("spinnaker-app", "myapp");
  }

  // ── deriveMoniker: all five tags present ───────────────────────────────────

  @Test
  void allFiveTagsPresentUsesThemExactly() {
    ProxmoxResource resource =
        resource(
            "ignored-name",
            "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-stack+prod;"
                + "spinnaker-detail+detail1;spinnaker-sequence+3");

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("myapp");
    assertThat(m.getCluster()).isEqualTo("myapp-prod");
    assertThat(m.getStack()).isEqualTo("prod");
    assertThat(m.getDetail()).isEqualTo("detail1");
    assertThat(m.getSequence()).isEqualTo(3);
  }

  // ── deriveMoniker: app + stack only → cluster derived ─────────────────────

  @Test
  void appAndStackOnlyDerivesClusterName() {
    ProxmoxResource resource = resource("whatever", "spinnaker-app+myapp;spinnaker-stack+prod");

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("myapp");
    assertThat(m.getStack()).isEqualTo("prod");
    // cluster should be derived from app+stack
    assertThat(m.getCluster()).isEqualTo("myapp-prod");
  }

  // ── deriveMoniker: no tags, Frigga name ───────────────────────────────────

  @Test
  void noTagsFallsBackToFriggaNameParsing() {
    ProxmoxResource resource = resource("myapp-prod-v001", null);

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("myapp");
    assertThat(m.getStack()).isEqualTo("prod");
    assertThat(m.getSequence()).isEqualTo(1);
  }

  // ── deriveMoniker: tags override Frigga ───────────────────────────────────

  @Test
  void tagsOverrideFriggaName() {
    // Name would parse as app=someapp, but tag says spinnaker-app=override
    ProxmoxResource resource = resource("someapp-v1", "spinnaker-app+override");

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("override");
  }

  // ── deriveMoniker: null/empty tags string ─────────────────────────────────

  @Test
  void emptyTagsStringFallsBackToFriggaGracefully() {
    ProxmoxResource resource = resource("myapp-prod-v002", "");

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("myapp");
    assertThat(m.getStack()).isEqualTo("prod");
  }

  @Test
  void nullTagsStringFallsBackToFriggaGracefully() {
    ProxmoxResource resource = resource("myapp-staging", null);

    Moniker m = namer.deriveMoniker(resource);

    assertThat(m.getApp()).isEqualTo("myapp");
    assertThat(m.getStack()).isEqualTo("staging");
  }

  // ── getNamingStrategyName ─────────────────────────────────────────────────

  @Test
  void getNamingStrategyNameReturnsProxmoxTags() {
    assertThat(namer.getName()).isEqualTo("proxmoxTags");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ProxmoxResource resource(String name, String tags) {
    return new ProxmoxResource() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getNode() {
        return "pve01";
      }

      @Override
      public String getTags() {
        return tags;
      }

      @Override
      public Integer getVmId() {
        return 101;
      }
    };
  }
}
