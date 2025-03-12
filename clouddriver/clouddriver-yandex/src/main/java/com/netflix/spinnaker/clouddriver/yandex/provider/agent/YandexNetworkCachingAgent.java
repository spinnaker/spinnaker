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
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudNetwork;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class YandexNetworkCachingAgent extends AbstractYandexCachingAgent<YandexCloudNetwork> {
  private static final String TYPE = Keys.Namespace.NETWORKS.getNs();

  public YandexNetworkCachingAgent(
      YandexCloudCredentials credentials,
      ObjectMapper objectMapper,
      YandexCloudFacade yandexCloudFacade) {
    super(credentials, objectMapper, yandexCloudFacade);
  }

  @Override
  public Set<AgentDataType> getProvidedDataTypes() {
    return Collections.singleton(Authority.AUTHORITATIVE.forType(TYPE));
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  protected List<YandexCloudNetwork> loadEntities(ProviderCache providerCache) {
    return yandexCloudFacade.getNetworks(credentials);
  }

  @Override
  protected String getKey(YandexCloudNetwork network) {
    return Keys.getNetworkKey(
        network.getAccount(), network.getId(), credentials.getFolder(), network.getName());
  }
}
