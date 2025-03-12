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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexInstanceProvider implements InstanceProvider<YandexCloudInstance, String> {
  private AccountCredentialsProvider accountCredentialsProvider;
  private YandexCloudFacade yandexCloudFacade;
  private final CacheClient<YandexCloudInstance> cacheClient;

  @Autowired
  public YandexInstanceProvider(
      Cache cacheView,
      AccountCredentialsProvider accountCredentialsProvider,
      YandexCloudFacade yandexCloudFacade,
      ObjectMapper objectMapper) {
    this.yandexCloudFacade = yandexCloudFacade;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.INSTANCES, YandexCloudInstance.class);
  }

  @Override
  public String getCloudProvider() {
    return YandexCloudProvider.ID;
  }

  @Override
  public YandexCloudInstance getInstance(String account, String region, String name) {
    return getAccountCredentials(account)
        .map(credentials -> Keys.getInstanceKey(account, "*", credentials.getFolder(), name))
        .flatMap(cacheClient::findOne)
        .orElse(null);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    YandexCloudCredentials credentials =
        getAccountCredentials(account)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials: " + account));
    return Optional.ofNullable(getInstance(account, region, id))
        .map(instance -> yandexCloudFacade.getSerialPortOutput(credentials, instance.getId()))
        .orElse(null);
  }

  @NotNull
  private Optional<YandexCloudCredentials> getAccountCredentials(String account) {
    AccountCredentials<?> accountCredentials = accountCredentialsProvider.getCredentials(account);
    if (!(accountCredentials instanceof YandexCloudCredentials)) {
      return Optional.empty();
    }
    return Optional.of((YandexCloudCredentials) accountCredentials);
  }
}
