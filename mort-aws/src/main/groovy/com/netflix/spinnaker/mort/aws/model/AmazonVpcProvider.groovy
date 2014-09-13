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

import com.amazonaws.services.ec2.model.Vpc
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.VpcProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonVpcProvider implements VpcProvider<AmazonVpc> {

  private static final String NAME_TAG_KEY = 'Name'

  @Autowired
  CacheService cacheService

  @Override
  Set<AmazonSubnet> getAll() {
    def keys = cacheService.keysByType(Keys.Namespace.VPCS)
    keys.collect { String key ->
      def parts = Keys.parse(key)
      def vpc = cacheService.retrieve(key, Vpc)
      def tag = vpc.tags.find { it.key == NAME_TAG_KEY }
      String name = tag?.value
      new AmazonVpc(id: vpc.vpcId,
          name: name,
          account: parts.account,
          region: parts.region
      )
    }
  }
}
