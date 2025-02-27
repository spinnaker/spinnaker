/*
 * Copyright 2025 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.controllers;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.ImageService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = "spring.config.location=classpath:gate-test.yml")
class ImageControllerTest {

  private MockMvc webAppMockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean ImageService imageService;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc = webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void testFindImages() throws Exception {
    String provider = "my-provider";
    String query = "my-query";
    String region = "my-region";
    String account = "my-account";
    int count = 17;
    String rateLimitHeader = "my-rate-limit-header";

    Map<String, String> additionalFilters = Map.of("other", "my-special-header");

    webAppMockMvc
        .perform(
            get("/images/find")
                .param("provider", provider)
                .param("q", query)
                .param("region", region)
                .param("account", account)
                .param("count", String.valueOf(count))
                .param("other", "my-special-header")
                .header("X-RateLimit-Header", rateLimitHeader))
        .andDo(print())
        .andExpect(status().isOk());

    verify(imageService)
        .search(provider, query, region, account, count, additionalFilters, rateLimitHeader);
  }
}
