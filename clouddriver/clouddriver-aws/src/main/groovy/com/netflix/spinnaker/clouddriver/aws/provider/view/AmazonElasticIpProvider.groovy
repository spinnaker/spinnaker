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
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonElasticIp
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.ELASTIC_IPS

@Component
class AmazonElasticIpProvider implements ElasticIpProvider<AmazonElasticIp> {

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AmazonElasticIpProvider(Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<AmazonElasticIp> getAllByAccount(String account) {
    loadResults(cacheView.filterIdentifiers(ELASTIC_IPS.ns, Keys.getElasticIpKey('*', '*', account)))
  }

  @Override
  Set<AmazonElasticIp> getAllByAccountAndRegion(String account, String region) {
    loadResults(cacheView.filterIdentifiers(ELASTIC_IPS.ns, Keys.getElasticIpKey('*', region, account)))
  }

  Set<AmazonElasticIp> loadResults(Collection<String> identifiers) {
    cacheView.getAll(ELASTIC_IPS.ns, identifiers, RelationshipCacheFilter.none()).collect { CacheData data ->
      objectMapper.convertValue(data.attributes, AmazonElasticIp)
    }
  }
}
