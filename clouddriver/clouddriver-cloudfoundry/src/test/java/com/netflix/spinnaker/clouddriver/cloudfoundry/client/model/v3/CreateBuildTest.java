/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class CreateBuildTest {
  @Test
  void serialize() throws JacksonException {
    assertThat(JsonMapper.builder().build().writeValueAsString(new CreateBuild("123", 1024, 1024)))
        .isEqualTo(
            "{\"package\":{\"guid\":\"123\"},\"staging_memory_in_mb\":1024,\"staging_disk_in_mb\":1024}");
  }
}
