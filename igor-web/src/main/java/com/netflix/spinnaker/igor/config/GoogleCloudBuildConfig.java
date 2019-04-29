/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.gcb.*;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.netflix.spinnaker.igor.gcb")
@ConditionalOnProperty("gcb.enabled")
@EnableConfigurationProperties({
  GoogleCloudBuildProperties.class,
  IgorConfigurationProperties.class
})
public class GoogleCloudBuildConfig {
  @Bean
  HttpTransport httpTransport() throws IOException, GeneralSecurityException {
    return GoogleNetHttpTransport.newTrustedTransport();
  }

  @Bean
  GoogleCloudBuildAccountRepository googleCloudBuildAccountRepository(
      GoogleCloudBuildAccountFactory googleCloudBuildAccountFactory,
      GoogleCloudBuildProperties googleCloudBuildProperties) {
    GoogleCloudBuildAccountRepository credentials = new GoogleCloudBuildAccountRepository();
    googleCloudBuildProperties
        .getAccounts()
        .forEach(
            a -> {
              GoogleCloudBuildAccount account = googleCloudBuildAccountFactory.build(a);
              credentials.registerAccount(a.getName(), account);
            });
    return credentials;
  }

  @Bean
  GoogleCloudBuildCache.Factory googleCloudBuildCacheFactory(
      IgorConfigurationProperties igorConfigurationProperties,
      RedisClientDelegate redisClientDelegate,
      LockService lockService) {
    return new GoogleCloudBuildCache.Factory(
        lockService,
        redisClientDelegate,
        igorConfigurationProperties.getSpinnaker().getJedis().getPrefix());
  }

  @Bean
  GoogleCloudBuildClient.Factory googleCloudBuildClientFactory(
      CloudBuildFactory cloudBuildFactory, GoogleCloudBuildExecutor googleCloudBuildExecutor) {
    return new GoogleCloudBuildClient.Factory(
        cloudBuildFactory,
        googleCloudBuildExecutor,
        Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("Unknown"));
  }
}
