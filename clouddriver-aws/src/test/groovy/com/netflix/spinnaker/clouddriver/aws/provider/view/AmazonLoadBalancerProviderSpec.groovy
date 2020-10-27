/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

class AmazonLoadBalancerProviderSpec extends Specification {
  def cacheView = Mock(Cache)
  def awsProvider = Mock(AwsProvider)

  @Subject
  def provider = new AmazonLoadBalancerProvider(cacheView, awsProvider)

  def app = "app"
  def account = "some_long_account_name"
  def region = "us-east-1"
  def stack = "stack"
  def detail = "detail"
  def vpc = "vpc-a45e72d1"

  def "should get load balance by application"() {
    given:
    cacheView.getIdentifiers(LOAD_BALANCERS.ns) >> ["aws:loadBalancers:$account:$region:$app:$vpc",
                                                    "aws:loadBalancers:$account:$region:$app-$stack:$vpc:network",
                                                    "aws:loadBalancers:$account:$region:$app-$stack-$detail:$vpc:albFunction",
                                                    "aws:loadBalancers:$account:$region:wrong$app-$stack-$detail:$vpc:albFunction"]
    cacheView.getIdentifiers(TARGET_GROUPS.ns) >> []

    cacheView.getAll(_, _, _) >> { String collection, Set<String> keys, CacheFilter filter ->
      return keys.collect {
        new DefaultCacheData(
          it,
          [:],
          [:]
        )
      }
    }

    cacheView.getAll(TARGET_GROUPS.ns, _, _) >> []

    when: 'Requesting LBs for our app'
    def result = provider.getApplicationLoadBalancers(app)

    then: 'We get them all'
    result.size() == 3

    when: 'Requesting all network LBs'
    result = provider.getApplicationLoadBalancers("network")

    then: 'We get nothing since "network" is not the name of the app'
    result.size() == 0
  }
}
