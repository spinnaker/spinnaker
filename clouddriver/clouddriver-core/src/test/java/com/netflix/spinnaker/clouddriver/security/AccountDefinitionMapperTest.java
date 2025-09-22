/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.spinnaker.clouddriver.config.AccountDefinitionConfiguration;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import io.spinnaker.test.security.TestAccount;
import io.spinnaker.test.security.ValueAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AccountDefinitionConfiguration.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@TestPropertySource(
    properties = "account.storage.additionalScanPackages = io.spinnaker.test.security")
@ComponentScan("com.netflix.spinnaker.kork.secrets")
class AccountDefinitionMapperTest {

  @Autowired AccountDefinitionMapper mapper;

  @Test
  void canConvertAdditionalAccountTypes() throws JsonProcessingException {
    var account = new TestAccount();
    account.setData("name", "foo");
    account.getPermissions().add(Authorization.READ, Set.of("dev", "sre"));
    account.getPermissions().add(Authorization.WRITE, "sre");
    account.setData("password", "hunter2");
    assertEquals(account, mapper.deserialize(mapper.serialize(account)));
  }

  @Test
  void canConvertJacksonizedAccountTypes() throws JsonProcessingException {
    var account = ValueAccount.builder().name("james").value("meowth").build();
    assertEquals(account, mapper.deserialize(mapper.serialize(account)));
  }

  @Test
  void canDecryptSecretUris() {
    var data = "{\"type\":\"test\",\"name\":\"bar\",\"password\":\"secret://noop?v=hunter2&k=v\"}";
    CredentialsDefinition account = assertDoesNotThrow(() -> mapper.deserialize(data));
    assertThat(account).isInstanceOf(TestAccount.class);
    assertThat(account.getName()).isEqualTo("bar");
    TestAccount testAccount = (TestAccount) account;
    // due to how noop secret engine works, it returns the key's, "k", value, when
    // getSecretString is called. So we expect the returned value to be "v".
    assertThat(testAccount.getData().get("password")).isEqualTo("v");
  }

  @Test
  void canDecryptEncryptedUris() {
    var data = "{\"type\":\"test\",\"name\":\"bar\",\"password\":\"encrypted:noop!v:hunter2\"}";
    CredentialsDefinition account = assertDoesNotThrow(() -> mapper.deserialize(data));
    assertThat(account).isInstanceOf(TestAccount.class);
    assertThat(account.getName()).isEqualTo("bar");
    TestAccount testAccount = (TestAccount) account;
    assertThat(testAccount.getData().get("password")).isEqualTo("hunter2");
  }
}
