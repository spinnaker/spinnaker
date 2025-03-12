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

import com.amazonaws.services.ec2.model.Subnet
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSubnet
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SUBNETS

@Component
@Slf4j
class AmazonSubnetProvider implements SubnetProvider<AmazonSubnet> {
  private static final String METADATA_TAG_KEY = 'immutable_metadata'
  private static final String NAME_TAG_KEY = 'name'
  private static final String DEPRECATED_TAG_KEY = 'is_deprecated'

  private final Cache cacheView
  private final ObjectMapper amazonObjectMapper

  final String cloudProvider = AmazonCloudProvider.ID

  @Autowired
  AmazonSubnetProvider(Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper) {
    this.cacheView = cacheView
    this.amazonObjectMapper = amazonObjectMapper
  }

  @Override
  Set<AmazonSubnet> getAll() {
    getAllMatchingKeyPattern(Keys.getSubnetKey('*', '*', '*'))
  }

  Set<AmazonSubnet> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern))
  }

  Set<AmazonSubnet> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  AmazonSubnet fromCacheData(CacheData cacheData) {
    def parts = Keys.parse(cacheData.id)
    def subnet = amazonObjectMapper.convertValue(cacheData.attributes, Subnet)
    def tag = subnet.tags.find { it.key == METADATA_TAG_KEY }
    def isDeprecated = subnet.tags.find { it.key == DEPRECATED_TAG_KEY }?.value
    String json = tag?.value
    String purpose = null
    String target = null
    if (json) {
      def objectMapper = new ObjectMapper()
      try {
        def metadata = objectMapper.readValue((String) json, Map.class)
        purpose = metadata?.purpose
        target = metadata?.target
      } catch (JsonParseException e) {
        log.error("Can not extract purpose and/or target from ${METADATA_TAG_KEY}\n" +
          "\tAccount: ${parts.account? parts.account : "account not resolved"}\n" +
          "\tSubnet id: ${parts.id? parts.id: "subnet id not found"}\n" +
          "\tException message: ${e.message}")
      }
    }

    def name = subnet.tags.find { it.key.equalsIgnoreCase(NAME_TAG_KEY) }?.value
    if (name && !purpose) {
      def splits = name.split('\\.')
      if (splits.length == 3) {
        purpose = "${splits[1]} (${splits[0]})"
      }
    }

    new AmazonSubnet(
      type: AmazonCloudProvider.ID,
      id: subnet.subnetId,
      state: subnet.state,
      vpcId: subnet.vpcId,
      cidrBlock: subnet.cidrBlock,
      availableIpAddressCount: subnet.availableIpAddressCount,
      account: parts.account,
      accountId: cacheData.attributes.accountId,
      region: parts.region,
      availabilityZone: subnet.availabilityZone,
      purpose: purpose,
      target: target,
      deprecated: new Boolean(isDeprecated)
    )
  }
}
