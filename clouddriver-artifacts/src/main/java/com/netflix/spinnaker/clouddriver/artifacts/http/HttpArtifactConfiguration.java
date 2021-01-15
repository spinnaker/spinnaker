/*
 * Copyright 2018 Joel Wilsson
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.http;

import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.squareup.okhttp.OkHttpClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.http.enabled")
@EnableConfigurationProperties(HttpArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class HttpArtifactConfiguration {
  private final HttpArtifactProviderProperties httpArtifactProviderProperties;
  private final HttpArtifactAccount noAuthAccount =
      HttpArtifactAccount.builder().name("no-auth-http-account").build();

  @Bean
  public CredentialsTypeProperties<HttpArtifactCredentials, HttpArtifactAccount>
      httpCredentialsProperties(OkHttpClient okHttpClient) {
    return CredentialsTypeProperties.<HttpArtifactCredentials, HttpArtifactAccount>builder()
        .type(HttpArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(HttpArtifactCredentials.class)
        .credentialsDefinitionClass(HttpArtifactAccount.class)
        .defaultCredentialsSource(this::getHttpAccounts)
        .credentialsParser(
            a -> {
              try {
                return new HttpArtifactCredentials(a, okHttpClient);
              } catch (Exception e) {
                log.warn("Failure instantiating Http artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }

  private List<HttpArtifactAccount> getHttpAccounts() {
    List<HttpArtifactAccount> accounts = httpArtifactProviderProperties.getAccounts();
    if (accounts.stream().noneMatch(HttpArtifactAccount::usesAuth)) {
      accounts.add(noAuthAccount);
    }
    return accounts;
  }
}
