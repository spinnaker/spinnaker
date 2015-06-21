/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.mort.aws.provider

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.Provider

class AwsInfrastructureProvider implements Provider {
  public static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  public static final String PROVIDER_NAME = AwsInfrastructureProvider.name

  private final Collection<CachingAgent> agents

  AwsInfrastructureProvider(Collection<CachingAgent> agents) {
    this.agents = Collections.unmodifiableCollection(agents)
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Collection<CachingAgent> getCachingAgents() {
    agents
  }
}
