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

package com.netflix.spinnaker.clouddriver.yandex.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.yandex.deploy.converter.OperationConverter;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.CredentialsChangeable;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

public abstract class AbstractYandexDeployTest {
  private static final List<String> ACCOUNTS = Collections.singletonList("test-cred");
  private final AccountCredentialsRepository accountCredentialsRepository =
      new MapBackedAccountCredentialsRepository();
  protected AccountCredentialsProvider accountCredentialsProvider =
      new DefaultAccountCredentialsProvider(accountCredentialsRepository);
  protected ObjectMapper objectMapper =
      Jackson2ObjectMapperBuilder.json()
          .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .modules(new JavaTimeModule())
          .build();

  protected AbstractYandexDeployTest() {
    ACCOUNTS.forEach(
        account -> accountCredentialsRepository.update(account, createCredentials(account)));
  }

  private static YandexCloudCredentials createCredentials(String name) {
    YandexCloudCredentials cred = new YandexCloudCredentials();
    cred.setName(name);
    cred.setFolder("folder");
    return cred;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getDescription(String resourcePath) throws IOException {
    JsonNode json =
        objectMapper.readTree(AbstractYandexDeployTest.class.getResourceAsStream(resourcePath));
    return (Map<String, Object>) objectMapper.convertValue(json, Map.class);
  }

  public <T extends CredentialsChangeable> T getObject(String fileName, Class<T> clazz)
      throws IOException {
    Map<String, Object> input = getDescription(fileName);
    TestCredentialSupport credSupport = new TestCredentialSupport();
    credSupport.setAccountCredentialsProvider(accountCredentialsProvider);
    credSupport.setObjectMapper(objectMapper);
    return new OperationConverter<T, AtomicOperation<?>>(null, null)
        .convertDescription(input, credSupport, clazz);
  }
}
