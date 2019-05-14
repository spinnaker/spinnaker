/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import groovy.util.logging.Slf4j;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
class KubernetesV2Provider extends AgentSchedulerAware implements Provider {
  public static final String PROVIDER_NAME = KubernetesCloudProvider.getID();

  private Collection<Agent> agents = emptyAgentCollection();

  private Collection<Agent> nextAgentSet = emptyAgentCollection();

  private static Collection<Agent> emptyAgentCollection() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  public void addAllAgents(Collection<Agent> agents) {
    nextAgentSet.addAll(agents);
  }

  public void clearNewAgentSet() {
    nextAgentSet.clear();
  }

  public void switchToNewAgents() {
    Collection<Agent> nextAgentSetCopy = emptyAgentCollection();
    nextAgentSetCopy.addAll(nextAgentSet);
    agents = nextAgentSetCopy;
    clearNewAgentSet();
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }
}
