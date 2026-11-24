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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.InstanceGroupManagerInstanceFlexibilityPolicy;
import com.google.api.services.compute.model.InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.DiskOverride;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests mapping GCE instance selection overrides back to the Spinnaker model. */
class InstanceFlexibilityPolicyConversionTest {

  @Test
  void convertsMinCpuPlatformAndDiskOverrides() {
    InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection n4 =
        new InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection()
            .setRank(0)
            .setMachineTypes(List.of("n4-standard-4"))
            .setMinCpuPlatform("Intel Emerald Rapids")
            .setDisks(
                List.of(
                    new AttachedDisk()
                        .setBoot(true)
                        .setDeviceName("myapp-v001-0")
                        .setInitializeParams(
                            new AttachedDiskInitializeParams()
                                .setDiskType(
                                    "https://compute.googleapis.com/compute/v1/projects/p/zones/z/diskTypes/hyperdisk-balanced")
                                .setDiskSizeGb(50L)),
                    new AttachedDisk()
                        .setBoot(false)
                        .setDeviceName("myapp-v001-1")
                        .setInitializeParams(
                            new AttachedDiskInitializeParams().setDiskType("hyperdisk-balanced")),
                    new AttachedDisk()
                        .setBoot(false)
                        .setDeviceName("added-outside-spinnaker")
                        .setInitializeParams(
                            new AttachedDiskInitializeParams().setDiskType("pd-ssd"))));
    InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection e2 =
        new InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection()
            .setRank(1)
            .setMachineTypes(List.of("e2-standard-4"));
    InstanceGroupManagerInstanceFlexibilityPolicy gcePolicy =
        new InstanceGroupManagerInstanceFlexibilityPolicy()
            .setInstanceSelections(Map.of("n4", n4, "e2", e2));

    GoogleInstanceFlexibilityPolicy policy =
        AbstractGoogleServerGroupCachingAgent.convertInstanceFlexibilityPolicy(
            gcePolicy, "myapp-v001");

    GoogleInstanceFlexibilityPolicy.InstanceSelection n4Selection =
        policy.getInstanceSelections().get("n4");
    assertThat(n4Selection.getMinCpuPlatform()).isEqualTo("Intel Emerald Rapids");
    assertThat(n4Selection.getDisks())
        .containsExactly(
            new DiskOverride(true, null, "hyperdisk-balanced", 50L),
            new DiskOverride(null, 1, "hyperdisk-balanced", null));
    GoogleInstanceFlexibilityPolicy.InstanceSelection e2Selection =
        policy.getInstanceSelections().get("e2");
    assertThat(e2Selection.getMinCpuPlatform()).isNull();
    assertThat(e2Selection.getDisks()).isNull();
  }

  @Test
  void skipsDisksWhenServerGroupNameIsUnknown() {
    InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection n4 =
        new InstanceGroupManagerInstanceFlexibilityPolicyInstanceSelection()
            .setMachineTypes(List.of("n4-standard-4"))
            .setDisks(
                List.of(
                    new AttachedDisk()
                        .setDeviceName("myapp-v001-1")
                        .setInitializeParams(
                            new AttachedDiskInitializeParams().setDiskType("hyperdisk-balanced"))));

    GoogleInstanceFlexibilityPolicy policy =
        AbstractGoogleServerGroupCachingAgent.convertInstanceFlexibilityPolicy(
            new InstanceGroupManagerInstanceFlexibilityPolicy()
                .setInstanceSelections(Map.of("n4", n4)),
            null);

    assertThat(policy.getInstanceSelections().get("n4").getDisks()).isNull();
  }
}
