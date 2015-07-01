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

package com.netflix.spinnaker.oort.titan.caching.providers
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.model.InstanceProvider
import com.netflix.spinnaker.oort.titan.model.TitanInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES

@Component
class CatsInstanceProvider implements InstanceProvider<TitanInstance> {

  private final Cache cacheView

  @Autowired
  CatsInstanceProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Override
  TitanInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (!instanceEntry) {
      return null
    }
    TitanInstance instance = new TitanInstance(instanceEntry.attributes.task)
    if (instanceEntry.relationships[HEALTH.ns]) {
      instance.health = instance.health ?: []
      instance.health.addAll(cacheView.getAll(HEALTH.ns, instanceEntry.relationships[HEALTH.ns])*.attributes)
    }
    instance
  }

}
