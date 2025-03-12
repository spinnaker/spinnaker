/*
 * Copyright (c) 2019 Schibsted Media Group.
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
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormationStack
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.STACKS

class AmazonCloudFormationProviderSpec extends Specification {
  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  @Subject
  AmazonCloudFormationProvider provider

  ObjectMapper objectMapper = new ObjectMapper()

  def setup() {
    def cache = new InMemoryCache()
    cloudFormations.each {
      cache.merge(STACKS.ns,
          new DefaultCacheData(makeKey(it), objectMapper.convertValue(it, ATTRIBUTES), [:]))
    }

    provider = new AmazonCloudFormationProvider(cache, objectMapper)
  }

  @Unroll
  void "list all cloud formations by account (any region)"() {
    when:
    def result = provider.list(accountId, '*') as Set

    then:
    result == expected

    where:
    accountId  || expected
    "account1" || [ stack1, stack2 ] as Set
    "account2" || [ stack3 ] as Set
    "unknown"  || [] as Set
    null       || [] as Set
  }

  @Unroll
  void "list all cloud formations by account and region"() {
    when:
    def result = provider.list(account, region) as Set

    then:
    result == expected

    where:
    account     | region      || expected
    "account1"  | "region1"   || [ stack1 ] as Set
    "account1"  | "region2"   || [ stack2 ] as Set
    "account1"  | "region3"   || [] as Set
    "account1"  | null        || [] as Set
    "account2"  | "region1"   || [ stack3 ] as Set
    "unknown"   | "unknown"   || [] as Set
  }

  @Unroll
  void "get a cloud formation by stackId"() {
    when:
    def result = provider.get(stackId)

    then:
    result ==  expected

    where:
    stackId   || expected
    "stack1"  || Optional.of(stack1)
    "stack2"  || Optional.of(stack2)
    "stack3"  || Optional.of(stack3)
    "unkown"  || Optional.empty()
    null      || Optional.empty()
  }

  @Shared
  def stack1 = new AmazonCloudFormationStack(stackId: "stack1", region: "region1", accountId: "account1")
  @Shared
  def stack2 = new AmazonCloudFormationStack(stackId: "stack2", region: "region2", accountId: "account1")
  @Shared
  def stack3 = new AmazonCloudFormationStack(stackId: "stack3", region: "region1", accountId: "account2")

  @Shared
  Set<AmazonCloudFormationStack> cloudFormations = [stack1, stack2, stack3]

  private static String makeKey(AmazonCloudFormationStack stack) {
    Keys.getCloudFormationKey(stack.stackId, stack.region, stack.accountId)
  }

}
