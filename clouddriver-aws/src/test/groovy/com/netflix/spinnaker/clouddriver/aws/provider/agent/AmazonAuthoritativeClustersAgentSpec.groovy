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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import org.hamcrest.Matchers
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;

class AmazonAuthoritativeClustersAgentSpec extends Specification {

  def providerCache = Mock(ProviderCache)
  def subject = new AmazonAuthoritativeClustersAgent()

  def "caches clusters"() {

    when:
    def result = subject.loadData(providerCache)

    then:
    1 * providerCache.filterIdentifiers(SERVER_GROUPS.ns, Keys.getServerGroupKey('*','*', '*', '*')) >> sgIds
    1 * providerCache.existingIdentifiers(SERVER_GROUPS.ns, _) >> sgIds

    result.cacheResults.size() == 1
    result.cacheResults[CLUSTERS.ns].size() == expectedSize
    Matchers.containsInAnyOrder(expected.toArray()).matches(result.cacheResults[CLUSTERS.ns]*.id)
    Matchers.containsInAnyOrder("foo-main", "foo-staging").matches(result.cacheResults[CLUSTERS.ns]*.attributes["name"])

    where:
    sgIds = [
      Keys.getServerGroupKey("foo-main-v123", "prod", "us-east-1"),
      Keys.getServerGroupKey("foo-main-v122", "prod", "us-east-1"),
      Keys.getServerGroupKey("foo-main-v124", "prod", "us-west-2"),
      Keys.getServerGroupKey("foo-main-v123", "prod", "us-west-2"),
      Keys.getServerGroupKey("foo-staging-v122", "test", "us-east-1"),
      Keys.getServerGroupKey("foo-staging-v124", "test", "us-east-1")
    ]
    expected = [
      Keys.getClusterKey("foo-main", "foo","prod"),
      Keys.getClusterKey("foo-staging", "foo","test")
    ]
    expectedSize = expected.size()

  }
}
