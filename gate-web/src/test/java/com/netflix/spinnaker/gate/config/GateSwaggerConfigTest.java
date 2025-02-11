/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.gate.config;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.gate.Main;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(classes = Main.class)
@ActiveProfiles("swaggertest")
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class GateSwaggerConfigTest {

  @Autowired private MockMvc mockMvc;

  private static final String OPENAPI_API_PATH = "/v3/api-docs";

  @Test
  void TestSwaggerDocsIsNotMalformed() throws Exception {
    mockMvc
        .perform(get(OPENAPI_API_PATH))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.openapi", startsWith("3."))) // validates we use version 3.x.x from kork
        .andExpect(jsonPath("$.info.title", is("Spinnaker Test")));
  }
}
