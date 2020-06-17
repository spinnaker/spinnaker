/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesEnableDisableManifestDescriptionTest {
  private static final JsonNodeFactory jsonFactory = JsonNodeFactory.instance;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final int DEFAULT_TARGET_PERCENTAGE = 100;

  @Test
  void deserializeEmpty() throws Exception {
    String serialized = jsonFactory.objectNode().toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getLoadBalancers()).isNotNull();
    assertThat(description.getLoadBalancers()).isEmpty();
    assertThat(description.getTargetPercentage()).isEqualTo(DEFAULT_TARGET_PERCENTAGE);
  }

  @Test
  void deserializeNullLoadBalancers() throws Exception {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("loadBalancers", jsonFactory.nullNode())
            .toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getLoadBalancers()).isNotNull();
    assertThat(description.getLoadBalancers()).isEmpty();
  }

  @Test
  void deserializEmptyLoadBalancers() throws Exception {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("loadBalancers", jsonFactory.arrayNode())
            .toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getLoadBalancers()).isNotNull();
    assertThat(description.getLoadBalancers()).isEmpty();
  }

  @Test
  void deserializNonEmptyLoadBalancers() throws Exception {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("loadBalancers", jsonFactory.arrayNode().add("abc").add("def"))
            .toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getLoadBalancers()).isNotNull();
    assertThat(description.getLoadBalancers()).containsExactly("abc", "def");
  }

  @Test
  void deserializeTargetPercentage() throws Exception {
    String serialized = jsonFactory.objectNode().put("targetPercentage", 50).toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getTargetPercentage()).isEqualTo(50);
  }

  @Test
  void deserializeManifestNameLocation() throws Exception {
    String serialized =
        jsonFactory
            .objectNode()
            .put("manifestName", "replicaSet my-rs")
            .put("location", "my-namespace")
            .toString();
    KubernetesEnableDisableManifestDescription description =
        objectMapper.readValue(serialized, KubernetesEnableDisableManifestDescription.class);
    assertThat(description.getManifestName()).isEqualTo("replicaSet my-rs");
    assertThat(description.getLocation()).isEqualTo("my-namespace");
  }
}
