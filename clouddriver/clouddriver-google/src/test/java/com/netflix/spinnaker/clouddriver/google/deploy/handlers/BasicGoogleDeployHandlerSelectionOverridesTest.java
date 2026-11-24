/*
 * Copyright 2026 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection;
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription;
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.DiskOverride;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for instance flexibility selection minCpuPlatform and disk overrides. */
class BasicGoogleDeployHandlerSelectionOverridesTest {

  private static final String BOOT_IMAGE =
      "https://compute.googleapis.com/compute/v1/projects/p/global/images/my-image";

  private final BasicGoogleDeployHandler handler = new BasicGoogleDeployHandler();

  @Test
  void mapsTrimmedMinCpuPlatformPerSelection() {
    InstanceSelection n2 = new InstanceSelection(1, List.of("n2-standard-4"));
    n2.setMinCpuPlatform("  Intel Ice Lake ");
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setMinCpuPlatform("   ");
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n2", n2, "n4", n4));
    InstanceGroupManager igm = new InstanceGroupManager();

    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);

    Map<String, InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection> selections =
        igm.getInstanceFlexibilityPolicy().getInstanceSelections();
    assertThat(selections.get("n2").getMinCpuPlatform()).isEqualTo("Intel Ice Lake");
    assertThat(selections.get("n4").getMinCpuPlatform()).isNull();
  }

  @Test
  void bootOverrideCopiesTemplateBootDiskAndAppliesType() {
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setDisks(List.of(new DiskOverride(true, null, "hyperdisk-balanced", null)));
    InstanceSelection e2 = new InstanceSelection(1, List.of("e2-standard-4"));
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n4", n4, "e2", e2));
    InstanceGroupManager igm = new InstanceGroupManager();
    List<AttachedDisk> templateDisks = templateDisks();

    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);
    handler.applyInstanceSelectionDiskOverrides(description, igm, templateDisks);

    Map<String, InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection> selections =
        igm.getInstanceFlexibilityPolicy().getInstanceSelections();
    assertThat(selections.get("e2").getDisks()).isNull();
    List<AttachedDisk> disks = selections.get("n4").getDisks();
    assertThat(disks).hasSize(1);
    AttachedDisk boot = disks.get(0);
    assertThat(boot.getDeviceName()).isEqualTo("myapp-v001-0");
    assertThat(boot.getBoot()).isTrue();
    assertThat(boot.getAutoDelete()).isTrue();
    assertThat(boot.getType()).isEqualTo("PERSISTENT");
    assertThat(boot.getInitializeParams().getSourceImage()).isEqualTo(BOOT_IMAGE);
    assertThat(boot.getInitializeParams().getDiskSizeGb()).isEqualTo(50L);
    assertThat(boot.getInitializeParams().getDiskType()).isEqualTo("hyperdisk-balanced");
    assertThat(boot.getInitializeParams().getLabels()).containsEntry("app", "myapp");

    // The instance template disks must not be mutated.
    assertThat(templateDisks.get(0).getInitializeParams().getDiskType()).isEqualTo("pd-ssd");
  }

  @Test
  void indexOverrideTargetsPersistentDiskAndSkipsLocalSsd() {
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setDisks(
        List.of(
            new DiskOverride(null, 0, "hyperdisk-balanced", 100L),
            new DiskOverride(null, 1, "hyperdisk-balanced", null)));
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n4", n4));
    InstanceGroupManager igm = new InstanceGroupManager();

    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);
    handler.applyInstanceSelectionDiskOverrides(description, igm, templateDisks());

    List<AttachedDisk> disks =
        igm.getInstanceFlexibilityPolicy().getInstanceSelections().get("n4").getDisks();
    assertThat(disks)
        .extracting(AttachedDisk::getDeviceName)
        .containsExactly("myapp-v001-0", "myapp-v001-1");
    assertThat(disks.get(0).getInitializeParams().getDiskSizeGb()).isEqualTo(100L);
    assertThat(disks.get(1).getInitializeParams().getDiskType()).isEqualTo("hyperdisk-balanced");
    assertThat(disks.get(1).getInitializeParams().getDiskSizeGb()).isEqualTo(200L);
    assertThat(disks.get(1).getBoot()).isFalse();
  }

  @Test
  void indexOverrideIgnoresScratchDiskTypeCase() {
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setDisks(List.of(new DiskOverride(null, 1, "hyperdisk-balanced", null)));
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n4", n4));
    InstanceGroupManager igm = new InstanceGroupManager();
    List<AttachedDisk> templateDisks =
        List.of(
            new AttachedDisk().setBoot(false).setType("scratch").setDeviceName("local-ssd"),
            templateDisks().get(0),
            templateDisks().get(1));

    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);
    handler.applyInstanceSelectionDiskOverrides(description, igm, templateDisks);

    assertThat(igm.getInstanceFlexibilityPolicy().getInstanceSelections().get("n4").getDisks())
        .extracting(AttachedDisk::getDeviceName)
        .containsExactly("myapp-v001-1");
  }

  @Test
  void rejectsOverrideIndexOutsideTemplateDisks() {
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setDisks(List.of(new DiskOverride(null, 2, "hyperdisk-balanced", null)));
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n4", n4));
    InstanceGroupManager igm = new InstanceGroupManager();
    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.applyInstanceSelectionDiskOverrides(description, igm, templateDisks()));
    assertThat(e.getMessage()).contains("'n4'").contains("2 persistent disks");
  }

  @Test
  void rejectsOverridesWhenTemplateDisksAreUnavailable() {
    InstanceSelection n4 = new InstanceSelection(0, List.of("n4-standard-4"));
    n4.setDisks(List.of(new DiskOverride(true, null, "hyperdisk-balanced", null)));
    BasicGoogleDeployDescription description = regionalDescription(Map.of("n4", n4));
    InstanceGroupManager igm = new InstanceGroupManager();
    handler.setInstanceFlexibilityPolicyToInstanceGroup(description, igm);

    assertThrows(
        IllegalArgumentException.class,
        () -> handler.applyInstanceSelectionDiskOverrides(description, igm, null));
  }

  @Test
  void noOpWithoutFlexibilityPolicy() {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(true);
    InstanceGroupManager igm = new InstanceGroupManager();

    handler.applyInstanceSelectionDiskOverrides(description, igm, null);

    assertThat(igm.getInstanceFlexibilityPolicy()).isNull();
  }

  private static BasicGoogleDeployDescription regionalDescription(
      Map<String, InstanceSelection> selections) {
    BasicGoogleDeployDescription description = new BasicGoogleDeployDescription();
    description.setRegional(true);
    description.setApplication("myapp");
    description.setDistributionPolicy(new GoogleDistributionPolicy(List.of(), "BALANCED"));
    GoogleInstanceFlexibilityPolicy policy = new GoogleInstanceFlexibilityPolicy();
    policy.setInstanceSelections(new LinkedHashMap<>(selections));
    description.setInstanceFlexibilityPolicy(policy);
    return description;
  }

  /** Mirrors GCEUtil.buildAttachedDisks output: boot, data disk, then a local SSD. */
  private static List<AttachedDisk> templateDisks() {
    AttachedDisk boot =
        new AttachedDisk()
            .setBoot(true)
            .setAutoDelete(true)
            .setDeviceName("myapp-v001-0")
            .setType("PERSISTENT")
            .setInitializeParams(
                new AttachedDiskInitializeParams()
                    .setSourceImage(BOOT_IMAGE)
                    .setDiskSizeGb(50L)
                    .setDiskType("pd-ssd")
                    .setLabels(Map.of("app", "myapp")));
    AttachedDisk data =
        new AttachedDisk()
            .setBoot(false)
            .setAutoDelete(true)
            .setDeviceName("myapp-v001-1")
            .setType("PERSISTENT")
            .setInitializeParams(
                new AttachedDiskInitializeParams().setDiskSizeGb(200L).setDiskType("pd-ssd"));
    AttachedDisk localSsd =
        new AttachedDisk()
            .setBoot(false)
            .setAutoDelete(true)
            .setType("SCRATCH")
            .setInitializeParams(new AttachedDiskInitializeParams().setDiskType("local-ssd"));
    return List.of(boot, data, localSsd);
  }
}
