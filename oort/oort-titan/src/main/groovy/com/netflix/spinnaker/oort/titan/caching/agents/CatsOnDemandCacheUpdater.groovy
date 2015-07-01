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

package com.netflix.spinnaker.oort.titan.caching.agents

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import com.netflix.spinnaker.oort.titan.caching.TitanCachingProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CatsOnDemandCacheUpdater implements OnDemandCacheUpdater {

  private final TitanCachingProvider titanProvider
  private final CatsModule catsModule

  @Autowired
  public CatsOnDemandCacheUpdater(TitanCachingProvider titanProvider, CatsModule catsModule) {
    this.titanProvider = titanProvider
    this.catsModule = catsModule
  }

  private Collection<com.netflix.spinnaker.oort.aws.provider.agent.OnDemandAgent> getOnDemandAgents() {
    titanProvider.cachingAgents.findAll { it instanceof com.netflix.spinnaker.oort.aws.provider.agent.OnDemandAgent }  // TODO
  }

  @Override
  boolean handles(String type) {
    onDemandAgents.any { it.handles(type) }
  }

  @Override
  void handle(String type, Map<String, ? extends Object> data) {
    // TODO
  }
}
