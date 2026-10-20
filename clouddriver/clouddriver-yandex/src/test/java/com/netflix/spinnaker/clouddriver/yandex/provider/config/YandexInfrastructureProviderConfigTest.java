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

package com.netflix.spinnaker.clouddriver.yandex.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.yandex.provider.agent.AbstractYandexCachingAgent;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class YandexInfrastructureProviderConfigTest {
  @Test
  void cachingAgentsPreserveTimestampDates() {
    YandexCloudCredentials credentials = new YandexCloudCredentials();
    credentials.setName("test-account");
    AccountCredentialsRepository repository = mock(AccountCredentialsRepository.class);
    doReturn(Set.of(credentials)).when(repository).getAll();
    ObjectMapper mapper = JsonMapper.builder().build();
    var provider =
        new YandexInfrastructureProviderConfig()
            .yandexInfrastructureProvider(
                repository, mock(YandexCloudFacade.class), mapper, new NoopRegistry());

    assertThat(provider.getAgents()).hasSize(7);
    for (var agent : provider.getAgents()) {
      var cachingAgent = (AbstractYandexCachingAgent<?>) agent;
      var attributes =
          cachingAgent
              .getObjectMapper()
              .convertValue(Map.of("createdAt", new Date(1234)), Map.class);
      assertThat(attributes.get("createdAt")).isEqualTo(1234L);
    }
    assertThat(mapper.readTree(mapper.writeValueAsString(new Date(1234))).isString()).isTrue();
  }
}
