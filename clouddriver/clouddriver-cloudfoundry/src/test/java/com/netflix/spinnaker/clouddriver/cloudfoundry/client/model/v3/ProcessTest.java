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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProcessTest {

  @Test
  void buildObjectTest0() {
    ObjectMapper mapper = new ObjectMapper();

    Process.HealthCheck healthCheck =
        new Process.HealthCheck.HealthCheckBuilder().type(null).data(null).build();

    Process process = new Process().setHealthCheck(healthCheck);

    Map<String, Object> converted = mapper.convertValue(process, Map.class);

    assertThat(converted.entrySet().size()).isEqualTo(3);
    assertThat(converted.get("healthCheck")).isNull();
  }

  @Test
  void buildObjectTest1() {
    ObjectMapper mapper = new ObjectMapper();

    Process.HealthCheck healthCheck =
        new Process.HealthCheck.HealthCheckBuilder().type(null).data(null).build();

    Map<String, Object> converted = mapper.convertValue(healthCheck, Map.class);

    assertThat(converted.entrySet().size()).isEqualTo(0);
  }

  @Test
  void buildObjectTest2() {
    ObjectMapper mapper = new ObjectMapper();

    Process.HealthCheck healthCheck =
        new Process.HealthCheck.HealthCheckBuilder()
            .type(null)
            .data(new Process.HealthCheckData.HealthCheckDataBuilder().timeout(90).build())
            .build();

    Map<String, Object> converted = mapper.convertValue(healthCheck, Map.class);

    Map<String, Object> data = (Map) converted.get("data");
    assertThat(data).isNotNull();
    assertThat(((int) data.get("timeout"))).isEqualTo(90);
    assertThat(data.entrySet().size()).isEqualTo(1);
  }
}
