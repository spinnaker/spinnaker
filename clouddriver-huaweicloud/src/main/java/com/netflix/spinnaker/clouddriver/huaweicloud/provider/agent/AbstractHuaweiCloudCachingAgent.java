/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.clouddriver.huaweicloud.client.HuaweiCloudClient;
import com.netflix.spinnaker.clouddriver.huaweicloud.provider.HuaweiCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;

public abstract class AbstractHuaweiCloudCachingAgent implements CachingAgent, AccountAware {

  final HuaweiCloudNamedAccountCredentials credentials;
  final ObjectMapper objectMapper;
  final String region;

  public AbstractHuaweiCloudCachingAgent(
      HuaweiCloudNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    this.credentials = credentials;
    this.objectMapper = objectMapper;
    this.region = region;
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  @Override
  public String getProviderName() {
    return HuaweiCloudInfrastructureProvider.class.getName();
  }

  @Override
  public String getAgentType() {
    return String.format("%s/%s/%s", getAccountName(), region, getAgentName());
  }

  HuaweiCloudClient getCloudClient() {
    return credentials.getCloudClient();
  }

  abstract String getAgentName();
}
