/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import java.util.Map;

public abstract class AbstractTencentCloudCachingAgent implements CachingAgent, AccountAware {

  private final ObjectMapper objectMapper;
  private final String region;
  private final String accountName;
  private final TencentCloudNamedAccountCredentials credentials;
  private final String providerName = TencentCloudInfrastructureProvider.class.getName();
  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {};

  public AbstractTencentCloudCachingAgent(
      TencentCloudNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    this.credentials = credentials;
    this.objectMapper = objectMapper;
    this.region = region;
    this.accountName = credentials.getName();
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public final String getRegion() {
    return region;
  }

  public final String getAccountName() {
    return accountName;
  }

  public final TencentCloudNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public final String getProviderName() {
    return providerName;
  }
}
