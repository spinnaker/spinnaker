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

package com.netflix.spinnaker.orca.clouddriver.model;

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class TaskOwnerTest {
  private static final ObjectMapper objectMapper = OrcaObjectMapper.newInstance();

  @Test
  public void deserializeTaskOwner() throws IOException {
    String resource = getResource("clouddriver/model/tasks/owners/success.json");
    TaskOwner taskOwner = objectMapper.readValue(resource, TaskOwner.class);
    assertThat(taskOwner.getName()).isNotNull();
    assertThat(taskOwner.getName()).isEqualTo("spin-clouddriver-123");
  }
}
