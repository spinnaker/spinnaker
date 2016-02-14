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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonKeyPair
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.KEY_PAIRS

@Component
class AmazonKeyPairProvider implements KeyPairProvider<AmazonKeyPair> {

  private final AmazonCloudProvider amazonCloudProvider
  private final Cache cacheView

  @Autowired
  AmazonKeyPairProvider(AmazonCloudProvider amazonCloudProvider, Cache cacheView) {
    this.amazonCloudProvider = amazonCloudProvider
    this.cacheView = cacheView
  }

  @Override
  Set<AmazonKeyPair> getAll() {
    cacheView.getAll(KEY_PAIRS.ns, RelationshipCacheFilter.none()).collect { CacheData cacheData ->
      Map<String, String> parts = Keys.parse(amazonCloudProvider, cacheData.id)
      new AmazonKeyPair(
        parts.account,
        parts.region,
        cacheData.attributes.keyName as String,
        cacheData.attributes.keyFingerprint as String)
    }
  }
}
