/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.credentials.poller.PollerConfiguration;
import com.netflix.spinnaker.credentials.poller.PollerConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("!(" + AccountDefinitionPubsubConfiguration.ENABLED + ")")
@EnableConfigurationProperties(PollerConfigurationProperties.class)
public class AccountDefinitionPollingConfiguration {
  @Bean
  public PollerConfiguration pollerConfiguration(
      ObjectProvider<CredentialsLoader<? extends Credentials>> loaders,
      PollerConfigurationProperties properties) {
    return new PollerConfiguration(properties, loaders);
  }
}
