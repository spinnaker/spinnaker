/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleInstanceFlexibilityPolicyTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldCreatePolicyWithSelections() {
    GoogleInstanceFlexibilityPolicy.InstanceSelection primary =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
            1, List.of("c3-standard-16", "n2-standard-16", "c2-standard-16"));
    GoogleInstanceFlexibilityPolicy.InstanceSelection secondary =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
            2, List.of("n2d-standard-16", "t2d-standard-16"));

    GoogleInstanceFlexibilityPolicy policy =
        new GoogleInstanceFlexibilityPolicy(
            Map.of("most-preferred", primary, "second-choice", secondary));

    assertThat(policy.getInstanceSelections()).hasSize(2);
    assertThat(policy.getInstanceSelections().get("most-preferred").getRank()).isEqualTo(1);
    assertThat(policy.getInstanceSelections().get("most-preferred").getMachineTypes())
        .containsExactly("c3-standard-16", "n2-standard-16", "c2-standard-16");
    assertThat(policy.getInstanceSelections().get("second-choice").getRank()).isEqualTo(2);
    assertThat(policy.getInstanceSelections().get("second-choice").getMachineTypes())
        .containsExactly("n2d-standard-16", "t2d-standard-16");
  }

  @Test
  void shouldCreateEmptyPolicy() {
    GoogleInstanceFlexibilityPolicy policy = new GoogleInstanceFlexibilityPolicy();
    assertThat(policy.getInstanceSelections()).isNull();
  }

  @Test
  void shouldSerializeAndDeserialize() throws Exception {
    GoogleInstanceFlexibilityPolicy.InstanceSelection selection =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
            1, List.of("n2-standard-8", "c3-standard-8"));

    GoogleInstanceFlexibilityPolicy policy =
        new GoogleInstanceFlexibilityPolicy(Map.of("preferred", selection));

    String json = objectMapper.writeValueAsString(policy);
    GoogleInstanceFlexibilityPolicy deserialized =
        objectMapper.readValue(json, GoogleInstanceFlexibilityPolicy.class);

    assertThat(deserialized.getInstanceSelections()).hasSize(1);
    assertThat(deserialized.getInstanceSelections().get("preferred").getRank()).isEqualTo(1);
    assertThat(deserialized.getInstanceSelections().get("preferred").getMachineTypes())
        .containsExactly("n2-standard-8", "c3-standard-8");
  }

  @Test
  void shouldSupportEqualityCheck() {
    GoogleInstanceFlexibilityPolicy.InstanceSelection sel1 =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(1, List.of("n2-standard-8"));
    GoogleInstanceFlexibilityPolicy.InstanceSelection sel2 =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(1, List.of("n2-standard-8"));

    GoogleInstanceFlexibilityPolicy policy1 =
        new GoogleInstanceFlexibilityPolicy(Map.of("group1", sel1));
    GoogleInstanceFlexibilityPolicy policy2 =
        new GoogleInstanceFlexibilityPolicy(Map.of("group1", sel2));

    assertThat(policy1).isEqualTo(policy2);
    assertThat(policy1.hashCode()).isEqualTo(policy2.hashCode());
  }
}
