/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Main.class, TestConfig.class})
@TestPropertySource(
    properties = {
      "redis.enabled = false",
      "sql.enabled = false",
      "spring.application.name = clouddriver",
      "yandex.enabled = true",
      "services.front50.baseUrl = http://localhost",
      "services.fiat.enabled = false",
      "services.fiat.baseUrl = http://localhost"
    })
class YandexControllersTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void serviceAccountsList() throws Exception {
    mockMvc
        .perform(get("/yandex/serviceAcounts/{account}", TestConfig.ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].name", Matchers.equalTo(TestConfig.SA_NAME)));
  }

  @Test
  void imageFindByName() throws Exception {
    mockMvc
        .perform(get("/yandex/images/find?q={name}", TestConfig.IMAGE_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].imageId", Matchers.equalTo(TestConfig.IMAGE_ID)));
  }

  @Test
  void imageFindByTag() throws Exception {
    mockMvc
        .perform(get("/yandex/images/find?tag:key=value"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].imageId", Matchers.equalTo(TestConfig.IMAGE_ID)));
  }

  @Test
  void scalingActivities() throws Exception {
    mockMvc
        .perform(
            get(
                "/applications/{app}/clusters/{account}/{cluster}/yandex/serverGroups/{serverGroupName}/scalingActivities?region={region}",
                "anyApp",
                TestConfig.ACCOUNT,
                "anyCluster",
                TestConfig.SERVER_GROUP_NAME,
                YandexCloudProvider.REGION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[1].description", Matchers.equalTo(TestConfig.ACTIVITY)));
  }
}
