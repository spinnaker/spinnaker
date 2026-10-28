/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the ecs-native rollout/revision fields are serialized as flattened top-level JSON
 * keys exactly once (via {@code @JsonAnyGetter getExtraAttributes()}), and never as a duplicate
 * alongside a bean property. This is the contract the Deck clusters-view card header relies on: the
 * fields must reach the summary payload (through the {@code ServerGroupViewModel} DTO's
 * {@code @JsonAnyGetter} forwarding) and the full-object details payload, without a Jackson
 * duplicate-key conflict.
 */
public class EcsServerGroupSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void emitsRolloutAndRevisionFieldsAtTopLevelExactlyOnce() {
    EcsServerGroup serverGroup = new EcsServerGroup();
    serverGroup.setName("myapp-stack-detail");
    serverGroup.setTaskDefinitionRevision(42);
    serverGroup.setDeploymentId("ecs-svc/1234567890");
    serverGroup.setRolloutState("IN_PROGRESS");
    serverGroup.setRolloutStateReason("ECS deployment is in progress.");
    serverGroup.setIsNative(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> json = objectMapper.convertValue(serverGroup, Map.class);

    // Flattened to the top level by @JsonAnyGetter (not nested under "extraAttributes").
    assertEquals(42, json.get("taskDefinitionRevision"));
    assertEquals("ecs-svc/1234567890", json.get("deploymentId"));
    assertEquals("IN_PROGRESS", json.get("rolloutState"));
    assertEquals("ECS deployment is in progress.", json.get("rolloutStateReason"));
    assertEquals(true, json.get("isNative"));

    // The any-getter map itself must not surface as a nested property (that would indicate a
    // misconfigured @JsonAnyGetter and risk a duplicate key).
    assertFalse(json.containsKey("extraAttributes"));
  }

  @Test
  public void omitsFieldsForAClassicEcsServerGroupThatNeverSetsThem() {
    EcsServerGroup serverGroup = new EcsServerGroup();
    serverGroup.setName("myapp-stack-detail-v007");

    @SuppressWarnings("unchecked")
    Map<String, Object> json = objectMapper.convertValue(serverGroup, Map.class);

    // Classic ecs server groups never populate these, so they add nothing to the payload.
    assertFalse(json.containsKey("taskDefinitionRevision"));
    assertFalse(json.containsKey("deploymentId"));
    assertFalse(json.containsKey("rolloutState"));
    assertFalse(json.containsKey("rolloutStateReason"));
    // isNative is only emitted when true, so a classic server group must not carry it (Deck treats
    // its absence as "classic" and shows the disabled-sibling rollback).
    assertFalse(json.containsKey("isNative"));
  }
}
