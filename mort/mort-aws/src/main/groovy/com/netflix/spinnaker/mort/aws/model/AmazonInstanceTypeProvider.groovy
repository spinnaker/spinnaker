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




package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.InstanceTypeProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonInstanceTypeProvider implements InstanceTypeProvider<AmazonInstanceType> {

    @Autowired
    CacheService cacheService

    @Override
    Set<AmazonInstanceType> getAll() {
      def keys = cacheService.keysByType(Keys.Namespace.INSTANCE_TYPES)
      keys.collect { String key ->
          cacheService.retrieve(key, AmazonInstanceType)
      }
    }
}
