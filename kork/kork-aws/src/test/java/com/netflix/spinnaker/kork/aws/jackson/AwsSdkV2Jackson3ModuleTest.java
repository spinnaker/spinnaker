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

package com.netflix.spinnaker.kork.aws.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.Cluster;

class AwsSdkV2Jackson3ModuleTest {

  private static final Cluster CLUSTER =
      Cluster.builder()
          .clusterArn("arn:aws:ecs:::cluster/my-cluster")
          .clusterName("my-cluster")
          .status("ACTIVE")
          .capacityProviders("FARGATE", "FARGATE_SPOT")
          .build();

  @Test
  void jackson3OutputMatchesJackson2Shape() throws Exception {
    String jackson2 =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new AwsSdkV2Module())
            .writeValueAsString(CLUSTER);

    tools.jackson.databind.json.JsonMapper mapper3 =
        tools.jackson.databind.json.JsonMapper.builder()
            .addModule(new AwsSdkV2Jackson3Module())
            .build();
    String jackson3 = mapper3.writeValueAsString(CLUSTER);

    assertEquals(jackson2, jackson3);
  }

  @Test
  void jackson3RoundTripPreservesFields() throws Exception {
    tools.jackson.databind.json.JsonMapper mapper3 =
        tools.jackson.databind.json.JsonMapper.builder()
            .addModule(new AwsSdkV2Jackson3Module())
            .build();
    Cluster back = mapper3.readValue(mapper3.writeValueAsString(CLUSTER), Cluster.class);

    assertEquals("my-cluster", back.clusterName());
    assertEquals("ACTIVE", back.status());
    assertEquals(CLUSTER.capacityProviders(), back.capacityProviders());
    assertEquals(CLUSTER.clusterArn(), back.clusterArn());
  }
}
