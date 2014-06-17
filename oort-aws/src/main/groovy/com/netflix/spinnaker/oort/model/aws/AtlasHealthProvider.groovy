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

package com.netflix.spinnaker.oort.model.aws

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.cachers.AtlasHealthCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import com.netflix.spinnaker.oort.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AtlasHealthProvider implements HealthProvider {

  @Autowired
  CacheService cacheService

  @Override
  Health getHealth(String account, ServerGroup serverGroup, String instanceId) {
    if (!(serverGroup instanceof AmazonServerGroup)) {
      return null
    }
    Map health = (Map)cacheService.retrieve(Keys.getInstanceHealthKey(instanceId, account, serverGroup.region, AtlasHealthCachingAgent.PROVIDER_NAME))
    health ? new AtlasHealth(health) : null
  }
}
