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

package com.netflix.spinnaker.cats.provider

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.*
import com.netflix.spinnaker.cats.mem.InMemoryCache

class DefaultProviderCacheSpec extends CacheSpec {

    WriteableCache backingStore

    @Override
    Cache getSubject() {
        backingStore = new InMemoryCache()
        new DefaultProviderCache(backingStore, new NoopRegistry())
    }

    void populateOne(String type, String id, CacheData cacheData = createData(id)) {
        defaultProviderCache.putCacheResult('testAgent', [], new DefaultCacheResult((type): [cacheData]))
    }

    DefaultProviderCache getDefaultProviderCache() {
        getCache() as DefaultProviderCache
    }

    def 'explicit evictions are removed from the cache'() {
        setup:
        String agent = 'agent'
        CacheResult result = new DefaultCacheResult(test: [new DefaultCacheData('id', [id: 'id'], [:])])
        defaultProviderCache.putCacheResult(agent, [], result)

        when:
        def data = defaultProviderCache.get('test', 'id')

        then:
        data != null
        data.id == 'id'

        when:
        defaultProviderCache.putCacheResult(agent, [], new DefaultCacheResult([:], [test: ['id']]))
        data = defaultProviderCache.get('test', 'id')

        then:
        data == null
    }

    def 'multiple agents can cache the same data type'() {
        setup:
        String usEast1Agent = 'AwsProvider:test/us-east-1/ClusterCachingAgent'
        CacheResult testUsEast1 = buildCacheResult('test', 'us-east-1')
        String usWest2Agent = 'AwsProvider:test/us-west-2/ClusterCachingAgent'
        CacheResult testUsWest2 = buildCacheResult('test', 'us-west-2')
        defaultProviderCache.putCacheResult(usEast1Agent, ['serverGroup'], testUsEast1)
        defaultProviderCache.putCacheResult(usWest2Agent, ['serverGroup'], testUsWest2)

        when:
        def app = defaultProviderCache.get('application', 'testapp')

        then:
        app.attributes.accountName == 'test'
        app.relationships.serverGroup.sort() == ['test/us-east-1/testapp-test-v001', 'test/us-west-2/testapp-test-v001']
    }

    def "an agents deletions don't affect another agent"() {
        setup:
        String usEast1Agent = 'AwsProvider:test/us-east-1/ClusterCachingAgent'
        CacheResult testUsEast1 = buildCacheResult('test', 'us-east-1')
        String usWest2Agent = 'AwsProvider:test/us-west-2/ClusterCachingAgent'
        CacheResult testUsWest2 = buildCacheResult('test', 'us-west-2')
        defaultProviderCache.putCacheResult(usEast1Agent, ['serverGroup'], testUsEast1)
        defaultProviderCache.putCacheResult(usWest2Agent, ['serverGroup'], testUsWest2)

        when:
        def app = defaultProviderCache.get('application', 'testapp')

        then:
        app.attributes.accountName == 'test'
        app.relationships.serverGroup.sort() == ['test/us-east-1/testapp-test-v001', 'test/us-west-2/testapp-test-v001']

        when:
        testUsEast1 = buildCacheResult('test', 'us-east-1', 'v002')
        defaultProviderCache.putCacheResult(usEast1Agent, ['serverGroup'], testUsEast1)
        app = defaultProviderCache.get('application', 'testapp')

        then:
        app.relationships.serverGroup.sort() == ['test/us-east-1/testapp-test-v002', 'test/us-west-2/testapp-test-v001']

    }

    def "items can be evicted by type and id"() {
        setup:
        String usEast1Agent = 'AwsProvider:test/us-east-1/ClusterCachingAgent'
        CacheResult testUsEast1 = buildCacheResult('test', 'us-east-1')
        defaultProviderCache.putCacheResult(usEast1Agent, ['serverGroup'], testUsEast1)

        when:
        def sg = defaultProviderCache.get('serverGroup', 'test/us-east-1/testapp-test-v001')

        then:
        sg != null

        when:
        defaultProviderCache.evictDeletedItems('serverGroup', ['test/us-east-1/testapp-test-v001'])
        sg = defaultProviderCache.get('serverGroup', 'test/us-east-1/testapp-test-v001')

        then:
        sg == null
    }

    private CacheResult buildCacheResult(String account, String region, String sgVersion = 'v001') {
        String serverGroup = "$account/$region/testapp-test-$sgVersion"
        String cluster = "$account/testapp-test"
        String application = 'testapp'
        String loadbalancer = "$account/$region/testapp--frontend"
        Map<String, Object> serverGroupAtts = [
                name   : 'testapp-test-v001',
                account: account,
                region : region
        ]

        CacheData app = new DefaultCacheData(application, [accountName: account], [serverGroup: [serverGroup], cluster: [cluster]])
        CacheData sg = new DefaultCacheData(serverGroup, serverGroupAtts, [application: [application], cluster: [cluster], loadBalancer: [loadbalancer]])
        CacheData clu = new DefaultCacheData(cluster, [:], [application: [application], serverGroup: [serverGroup]])
        CacheData lb = new DefaultCacheData(loadbalancer, [:], [serverGroup: [serverGroup]])

        new DefaultCacheResult([application: [app], serverGroup: [sg], cluster: [clu], loadBalancer: [lb]])
    }
}
