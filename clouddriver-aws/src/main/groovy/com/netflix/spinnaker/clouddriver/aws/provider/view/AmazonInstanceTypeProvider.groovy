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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonInstanceType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.INSTANCE_TYPES

@Component
class AmazonInstanceTypeProvider implements InstanceTypeProvider<AmazonInstanceType> {

  private final Cache cacheView
  private final ObjectMapper objectMapper
  private final AmazonInstanceTypeProviderConfiguration amazonInstanceTypeProviderConfiguration

  @Autowired
  AmazonInstanceTypeProvider(Cache cacheView, ObjectMapper objectMapper, AmazonInstanceTypeProviderConfiguration amazonInstanceTypeProviderConfiguration) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
    this.amazonInstanceTypeProviderConfiguration = amazonInstanceTypeProviderConfiguration
  }

  @Override
  Set<AmazonInstanceType> getAll() {
    cacheView.getAll(INSTANCE_TYPES.ns, RelationshipCacheFilter.none()).collect { objectMapper.convertValue(it.attributes, AmazonInstanceType) }.findAll(this.&includeInstance)
  }

  boolean includeInstance(AmazonInstanceType amazonInstanceType) {
    def excludedType = amazonInstanceTypeProviderConfiguration?.excluded?.find { amazonInstanceType.name =~ /^$it.name/ }
    if (!excludedType) {
      return true
    }

    if (!excludedType.regions || excludedType.regions.find { it == amazonInstanceType.region }) {
      return false
    }

    return true
  }
}
