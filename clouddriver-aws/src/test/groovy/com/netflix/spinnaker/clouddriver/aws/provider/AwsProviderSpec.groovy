/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider

import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.INSTANCES

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AwsProviderSpec extends Specification {

  def account = "test"
  def region = new AmazonCredentials.AWSRegion("us-east-1", [])
  NetflixAmazonCredentials credentials = Stub(NetflixAmazonCredentials) {
    getName() >> "test"
    getRegions() >> ([region] as List)
  }

  AccountCredentialsRepository credentialsRepository = Stub(AccountCredentialsRepository) {
    getAll() >> ([credentials] as Set)
  }

  Cache cache = Mock(Cache)
  AwsProvider.InstanceIdentifierExtractor extractor = new AwsProvider.InstanceIdentifierExtractor(credentialsRepository)

  @Unroll
  void 'InstanceIdentifierExtractor matches prefixed query: #query'() {
    when:
    extractor.getIdentifiers(cache, "instances", query)

    then:
    1 * cache.getAll(INSTANCES.ns, [Keys.getInstanceKey(query, account, region.name)])

    where:
    query << ['i-12345678', 'i-ef345678', 'i-12345678901234567', 'i-ef345678901234567', 'i-ef345678901234abc']
  }

  @Unroll
  void 'InstanceIdentifierExtractor matches unprefixed query: #query'() {
    when:
    extractor.getIdentifiers(cache, "instances", query)

    then:
    1 * cache.getAll(INSTANCES.ns, [Keys.getInstanceKey('i-' + query, account, region.name)])

    where:
    query << ['12345678', 'ef345678', '12345678901234567', 'ef345678901234567', 'ef345678901234abc']
  }

  @Unroll
  void "InstanceIdentifierExtractor does NOT match invalid query: #query"() {
    when:
    def result = extractor.getIdentifiers(cache, "instances", query)

    then:
    0 * cache.getAll(INSTANCES.ns, _)
    result == []

    where:
    query << ['1234567', 'efg45678', '1234567890123456', 'efg45678901234567', 'ef345678901234abx',
              'i-1234567', 'i-efg45678', 'i-1234567890123456', 'i-efg45678901234567', 'i-ef345678901234abx',
              'I-12345678', 'i--12345678']
  }
}
