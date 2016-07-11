/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS

/**
 * Provides a view of existing Openstack security groups in all configured Openstack accounts.
 */
@Slf4j
@Component
class OpenstackSecurityGroupProvider implements SecurityGroupProvider<OpenstackSecurityGroup> {

  final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  OpenstackSecurityGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getType() {
    OpenstackCloudProvider.ID
  }

  @Override
  Set<OpenstackSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', region), includeRules)
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', account, '*'), includeRules)
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', account, '*'), includeRules)
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', account, region), includeRules)
  }

  @Override
  OpenstackSecurityGroup get(String account, String region, String name, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', account, region), true)[0]
  }

  private Set<OpenstackSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  private Set<OpenstackSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    Closure<OpenstackSecurityGroup> handleRules = includeRules ? {x -> x} : this.&stripRules
    Collection<CacheData> data = cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    data.collect(this.&fromCacheData).collect(handleRules)
  }

  private OpenstackSecurityGroup fromCacheData(CacheData cacheData) {
    objectMapper.convertValue(cacheData.attributes, OpenstackSecurityGroup)
  }

  private OpenstackSecurityGroup stripRules(OpenstackSecurityGroup securityGroup) {
    new OpenstackSecurityGroup(id: securityGroup.id,
      accountName: securityGroup.accountName,
      region: securityGroup.region,
      name: securityGroup.name,
      description: securityGroup.description,
      inboundRules: []
    )
  }
}
