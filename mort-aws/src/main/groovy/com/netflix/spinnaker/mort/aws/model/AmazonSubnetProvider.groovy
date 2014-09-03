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
import com.amazonaws.services.ec2.model.Subnet
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.SubnetProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonSubnetProvider implements SubnetProvider<AmazonSubnet> {

    private static final String METADATA_TAG_KEY = 'immutable_metadata'

    @Autowired
    CacheService cacheService

    @Override
    Set<AmazonSubnet> getAll() {
      def keys = cacheService.keysByType(Keys.Namespace.SUBNETS)
      keys.collect { String key ->
          def parts = key.split(':')
          String account = parts[2]
          String region = parts[3]
          def subnet = cacheService.retrieve(key, Subnet)
          def tag = subnet.tags.find { it.key == METADATA_TAG_KEY }
          String json = tag?.value
          String purpose = null
          String target = null
          if (json) {
              def objectMapper = new ObjectMapper()
              def metadata = objectMapper.readValue((String) json, Map.class)
              metadata?.purpose
              purpose = metadata?.purpose
              target = metadata?.target
          }
          new AmazonSubnet(id: subnet.subnetId,
                  state: subnet.state,
                  vpcId: subnet.vpcId,
                  cidrBlock: subnet.cidrBlock,
                  availableIpAddressCount: subnet.availableIpAddressCount,
                  account: account,
                  region: region,
                  availabilityZone: subnet.availabilityZone,
                  purpose: purpose,
                  target: target
          )
      }
    }
}
