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
import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * A provider for amazon security groups
 */
@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider {

  @Autowired
  CacheService cacheService

  String type = "aws"

  @Override
  Set<SecurityGroup> getAll() {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS)
    (List<AmazonSecurityGroup>) keys.collect { retrieve(it) }
  }

  @Override
  Set<SecurityGroup> getAllByRegion(String region) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.region == region
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<SecurityGroup> getAllByAccount(String account) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndRegion(String account, String region) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account && parts.region == region
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndName(String account, String name) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account && parts.name == name
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  SecurityGroup get(String account, String region, String name, String vpcId) {
    def key = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).find { key ->
      def parts = Keys.parse(key)
      parts.account == account && parts.name == name && parts.region == region && parts.vpcId == vpcId
    }
    key ? retrieve(key) : null
  }

  private SecurityGroup retrieve(String key) {
    cacheService.retrieve(key, AmazonSecurityGroup)
  }

}
