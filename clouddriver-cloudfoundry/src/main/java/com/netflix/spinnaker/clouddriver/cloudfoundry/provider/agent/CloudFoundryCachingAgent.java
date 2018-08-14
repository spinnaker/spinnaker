/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@RequiredArgsConstructor
@Getter
@Slf4j
public class CloudFoundryCachingAgent implements CachingAgent, AccountAware {
  final String providerName = CloudFoundryProvider.class.getName();
  final Collection<AgentDataType> providedDataTypes = Arrays.asList(
    AgentDataType.Authority.AUTHORITATIVE.forType(Namespace.APPLICATIONS.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Namespace.CLUSTERS.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Namespace.SERVER_GROUPS.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Namespace.INSTANCES.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Namespace.LOAD_BALANCERS.ns)
  );
  private final CloudFoundryCredentials credentials;
  private final ObjectMapper objectMapper;

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Caching all resources in Cloud Foundry account $accountName");

    // todo(jkschneider): cache all Cloud Foundry resources
    return new DefaultCacheResult(Collections.emptyMap());
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getClass().getSimpleName();
  }
}
