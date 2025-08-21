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

package com.netflix.spinnaker.cats.agent;

import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.kork.annotations.Beta;

@Beta
public interface Agent {
  /**
   * @return the type of this agent.
   */
  String getAgentType();

  /**
   * @return name of this Agent's provider
   * @see com.netflix.spinnaker.cats.provider.ProviderRegistry
   */
  String getProviderName();

  AgentExecution getAgentExecution(ProviderRegistry providerRegistry);

  public default boolean handlesAccount(String accountName) {
    if (this instanceof AccountAware) {
      return accountName.equals(((AccountAware) this).getAccountName());
    }

    return false;
  }
}
