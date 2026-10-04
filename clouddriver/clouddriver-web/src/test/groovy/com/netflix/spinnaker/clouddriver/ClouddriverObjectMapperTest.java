/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "redis.enabled=false",
      "sql.enabled=false",
      "spring.application.name=clouddriver",
      "spring.jackson.mapper.SORT_PROPERTIES_ALPHABETICALLY=true"
    })
class ClouddriverObjectMapperTest {
  @Autowired private ObjectMapper mapper;
  @Autowired private AccountDefinitionMapper accountDefinitionMapper;

  @Test
  void accountSerializationPreservesOAuthPropertyNames() {
    ManagedAccount account = new ManagedAccount();
    account.setName("test-account");
    account.setOAuthServiceAccount("service-account");
    account.setOAuthScopes(List.of("scope"));

    JsonNode json = mapper.readTree(mapper.writeValueAsString(account));
    assertThat(json.path("oauthServiceAccount").asString()).isEqualTo("service-account");
    assertThat(json.path("oauthScopes").get(0).asString()).isEqualTo("scope");
    assertThat(json.has("oAuthServiceAccount")).isFalse();
    assertThat(json.has("oAuthScopes")).isFalse();
  }

  @Test
  void accountDeserializationReadsLegacyOAuthPropertyNames() {
    String json =
        "{\"name\":\"test-account\",\"type\":\"kubernetes\","
            + "\"oauthServiceAccount\":\"service-account\",\"oauthScopes\":[\"scope\"]}";
    ManagedAccount account = mapper.readValue(json, ManagedAccount.class);
    assertThat(account.getOAuthServiceAccount()).isEqualTo("service-account");
    assertThat(account.getOAuthScopes()).containsExactly("scope");
    ManagedAccount storedAccount = (ManagedAccount) accountDefinitionMapper.deserialize(json);
    assertThat(storedAccount.getOAuthServiceAccount()).isEqualTo("service-account");
    assertThat(storedAccount.getOAuthScopes()).containsExactly("scope");
  }
}
