/*
 * Copyright 2021 Netflix, Inc.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

public class KubernetesManifestReplicasTest {
  private static final JsonNodeFactory jsonFactory = JsonNodeFactory.instance;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void replicasValueShouldBeNullWithoutSpecDefined() throws JsonProcessingException {
    String serialized = jsonFactory.objectNode().toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);
    assertThat(manifest.getReplicas()).isNull();
  }

  @Test
  public void replicasValueShouldBeNullWithoutReplicasDefined() throws JsonProcessingException {
    String serialized =
        jsonFactory.objectNode().<ObjectNode>set("spec", jsonFactory.objectNode()).toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);
    assertThat(manifest.getReplicas()).isNull();
  }

  @Test
  public void shouldGetReplicasDoubleValue() throws JsonProcessingException {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("spec", jsonFactory.objectNode().put("replicas", 2.0))
            .toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);
    assertThat(manifest.getReplicas()).isEqualTo(2);
  }

  @Test
  public void shouldGetReplicasIntegerValue() throws JsonProcessingException {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("spec", jsonFactory.objectNode().put("replicas", 2))
            .toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);
    assertThat(manifest.getReplicas()).isEqualTo(2);
  }

  @Test
  public void replicasValueShouldRemainUnsetWithoutSpecDefined() throws JsonProcessingException {
    String serialized = jsonFactory.objectNode().toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);

    manifest.setReplicas(2.0);

    assertThat(manifest.getReplicas()).isEqualTo(null);
  }

  @Test
  public void replicasValueShouldBeSetWithReplicasDefined() throws JsonProcessingException {
    String serialized =
        jsonFactory
            .objectNode()
            .<ObjectNode>set("spec", jsonFactory.objectNode().put("replicas", 1))
            .toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);

    manifest.setReplicas(2.0);

    assertThat(manifest.getReplicas()).isEqualTo(2);
  }

  @Test
  public void replicasValueShouldBeSetWithReplicasUnDefined() throws JsonProcessingException {
    String serialized =
        jsonFactory.objectNode().<ObjectNode>set("spec", jsonFactory.objectNode()).toString();
    KubernetesManifest manifest = objectMapper.readValue(serialized, KubernetesManifest.class);

    manifest.setReplicas(2.0);

    assertThat(manifest.getReplicas()).isEqualTo(2);
  }
}
