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

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRefreshListener;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.kork.pubsub.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Broadcast refresh is opt-in and only replaces polling when SQL account storage is enabled. */
@Configuration
@ConditionalOnExpression(AccountDefinitionPubsubConfiguration.ENABLED)
@Import(RedisPubsubConfig.class)
public class AccountDefinitionPubsubConfiguration {
  public static final String ENABLED =
      "${sql.enabled:false} && ${account.storage.enabled:true} && ${account.storage.pubsub.enabled:false}";

  @Bean
  public MessageChannel accountDefinitionNotificationChannel(
      RedisPubsub pubsub, AccountDefinitionConfiguration.Properties properties) {
    return pubsub.broadcast(properties.getPubsub().getTopic());
  }

  @Bean
  public AccountDefinitionRefreshListener accountDefinitionRefreshListener(
      @Qualifier("accountDefinitionNotificationChannel") MessageChannel channel,
      ObjectProvider<CredentialsLoader<? extends Credentials>> loaders,
      AccountDefinitionConfiguration.Properties properties) {
    return new AccountDefinitionRefreshListener(
        channel,
        loaders,
        properties.getPubsub().getReclaimIdleMs(),
        properties.getPubsub().getStreamMaxLength());
  }
}
