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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.model.ElasticIp
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonElasticIp
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.ELASTIC_IPS

class AmazonElasticIpProviderSpec extends Specification {
  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  @Subject
  AmazonElasticIpProvider provider

  ObjectMapper objectMapper = new ObjectMapper()

  def setup() {
    def cache = new InMemoryCache()
    getAllElasticIps().each {
      cache.merge(ELASTIC_IPS.ns,
          new DefaultCacheData(makeKey(it), objectMapper.convertValue(it, ATTRIBUTES), [:]))
    }

    provider = new AmazonElasticIpProvider(cache, objectMapper)
  }

  @Unroll
  void "getAllByAccount lists elastic ips in the supplied account (any region)"() {
    when:
    def result = provider.getAllByAccount(account)

    then:
    result == getAllElasticIps().findAll { it.accountName == account } as Set

    where:
    account   || count
    "prod"    || 2
    "test"    || 2
    "unknown" || 0
    null      || 0
  }

  @Unroll
  void "getAllByAccountAndRegion lists elastic ips in the supplied account and region"() {
    when:
    def result = provider.getAllByAccountAndRegion(account, region)

    then:
    result == getAllElasticIps().findAll { it.accountName == account && it.region == region} as Set

    where:
    account | region      || count
    "prod"  | "us-west-1" || 1
    "prod"  | "us-west-2" || 0
    "prod"  | null        || 0
    "prod"  | ""          || 0
    "test"  | "us-east-1" || 1
  }

  @Shared
  Map elasticIpMap = [
    prod: [
      'us-east-1': [
        new AmazonElasticIp(address: '10.0.0.1', region: 'us-east-1', accountName: 'prod'),
      ],
      'us-west-1': [
        new AmazonElasticIp(address: '10.0.0.2', region: 'us-west-1', accountName: 'prod'),
      ]
    ],
    test: [
      'us-east-1': [
        new AmazonElasticIp(address: '10.0.1.1', region: 'us-east-1', accountName: 'test'),
      ],
      'us-west-1': [
        new AmazonElasticIp(address: '10.0.1.1', region: 'us-west-1', accountName: 'test'),
      ]
    ]
  ]

  private List<AmazonElasticIp> getAllElasticIps() {
    elasticIpMap.collect {
      it.value.collect {
        it.value
      }.flatten()
    }.flatten()
  }

  private static String makeKey(ElasticIp elasticIp) {
    Keys.getElasticIpKey(elasticIp.address, elasticIp.region, elasticIp.accountName)
  }

}
