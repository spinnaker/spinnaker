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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServiceAccount;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.List;

public class YandexServiceAccountCachingAgent
    extends AbstractYandexCachingAgent<YandexCloudServiceAccount> {
  public static final String TYPE = Keys.Namespace.SERVICE_ACCOUNT.getNs();

  public YandexServiceAccountCachingAgent(
      YandexCloudCredentials credentials,
      ObjectMapper objectMapper,
      YandexCloudFacade yandexCloudFacade) {
    super(credentials, objectMapper, yandexCloudFacade);
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected List<YandexCloudServiceAccount> loadEntities(ProviderCache providerCache) {
    return yandexCloudFacade.getServiceAccounts(credentials, getFolder());
  }

  @Override
  protected String getKey(YandexCloudServiceAccount sa) {
    return Keys.getServiceAccount(sa.getAccount(), sa.getId(), getFolder(), sa.getName());
  }
}
