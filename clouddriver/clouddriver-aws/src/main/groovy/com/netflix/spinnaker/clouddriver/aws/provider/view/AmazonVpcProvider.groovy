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

import com.amazonaws.services.ec2.model.Vpc
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonVpc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.VPCS

@Component
class AmazonVpcProvider implements NetworkProvider<AmazonVpc> {

  private static final String NAME_TAG_KEY = 'Name'
  private static final String DEPRECATED_TAG_KEY = 'is_deprecated'

  private final Cache cacheView
  private final ObjectMapper amazonObjectMapper

  @Autowired
  AmazonVpcProvider(Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper) {
    this.cacheView = cacheView
    this.amazonObjectMapper = amazonObjectMapper
  }

  @Override
  String getCloudProvider() {
    return AmazonCloudProvider.ID
  }

  @Override
  Set<AmazonVpc> getAll() {
    cacheView.getAll(VPCS.ns, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  AmazonVpc fromCacheData(CacheData cacheData) {
    def parts = Keys.parse(cacheData.id)
    def vpc = amazonObjectMapper.convertValue(cacheData.attributes, Vpc)
    def isDeprecated = vpc.tags.find { it.key == DEPRECATED_TAG_KEY }?.value
    new AmazonVpc(
      cloudProvider: AmazonCloudProvider.ID,
      id: vpc.vpcId,
      name: getVpcName(vpc),
      account: parts.account,
      region: parts.region,
      deprecated: new Boolean(isDeprecated)
    )
  }

  static String getVpcName(Vpc vpc) {
    vpc?.tags?.find { it.key == NAME_TAG_KEY }?.value
  }
}
