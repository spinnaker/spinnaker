/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CachingAgent

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.INSTANCES

interface HealthProvidingCachingAgent extends CachingAgent {
  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(HEALTH.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ])
  String getHealthId()
}
