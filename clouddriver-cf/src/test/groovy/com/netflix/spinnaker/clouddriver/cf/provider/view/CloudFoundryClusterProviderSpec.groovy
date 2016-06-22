/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.view
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.*
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.buildNativeApplication
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.mapToMeta
/**
 * @author Greg Turnquist
 */
class CloudFoundryClusterProviderSpec extends Specification {

	CloudFoundryClusterProvider clusterProvider

	CloudFoundryClient client

	ClusterCachingAgent cachingAgent

	ProviderRegistry registry

	// Generated via https://www.uuidgenerator.net/version4
	final String uuid1 = '35807c3d-d71b-486a-a7c7-0d351b62dace'
	final String uuid2 = 'e6d70139-5415-48b3-adf3-a35471f70ab5'
	final String uuid3 = '78d845c9-900e-4144-be09-63d4f433a2fd'

	def setup() {
		client = Mock(CloudFoundryClient)
		cachingAgent = new ClusterCachingAgent(
				new TestCloudFoundryClientFactory(stubClient: client),
				TestCredential.named('test'),
				new ObjectMapper(),
			 	new DefaultRegistry()
		)

		def cloudFoundryProvider = new CloudFoundryProvider([cachingAgent])
		registry = new DefaultProviderRegistry([cloudFoundryProvider],
				new InMemoryNamedCacheFactory())

		clusterProvider = new CloudFoundryClusterProvider(registry.getProviderCache(CloudFoundryProvider.PROVIDER_NAME),
				cloudFoundryProvider, new ObjectMapper())
	}

	def "should handle an empty cache"() {
		when:
		def clusters = clusterProvider.clusters

		then:
		clusters.size() == 0
	}

	def "should handle a cache miss"() {
		expect:
		clusterProvider.getClusters('foo', 'foo') == [] as Set
		clusterProvider.getCluster('foo', 'foo', 'foo') == null
		clusterProvider.getServerGroup('foo', 'foo', 'foo') == null
		clusterProvider.getClusterSummaries('foo') == null
		clusterProvider.getClusterDetails('foo') == null

	}

	def "should handle a basic application lookup"() {
		when:
		cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

		def clusters = clusterProvider.getClusters('testapp', 'test')
		def cluster = clusterProvider.getCluster('testapp', 'test', 'testapp-production')
		def serverGroup = clusterProvider.getServerGroup('test', 'spinnaker', 'testapp-production-v001')
		def summaries = clusterProvider.getClusterSummaries('testapp')
		def details = clusterProvider.getClusterDetails('testapp')

		then:
		clusters.size() == 1
		clusters[0].name == 'testapp-production'
		clusters[0].serverGroups.collect {it.name} == ['testapp-production-v001']
		clusters[0].accountName == 'test'

		cluster.name == 'testapp-production'
		cluster.accountName == 'test'

		cluster.serverGroups.size() == 1
		cluster.serverGroups[0].name == 'testapp-production-v001'

		cluster.loadBalancers.size() == 1
		cluster.loadBalancers[0].name == 'my-cool-test-app'

		serverGroup.name == 'testapp-production-v001'
		serverGroup.loadBalancers == ['my-cool-test-app'] as Set
		serverGroup.region == 'spinnaker'
		serverGroup.instances.collect {"${it.name} is ${it.healthState}"} == ['testapp-production-v001(0) is Up']
		serverGroup.createdTime == 1000L
		serverGroup.instanceCounts.up == 1
		serverGroup.instanceCounts.down == 0
		serverGroup.instanceCounts.starting == 0

		summaries.test.size() == 1
		summaries.test[0].name == 'testapp-production'
		summaries.test[0].accountName == 'test'
		summaries.test[0].serverGroups.collect {it.name} == ['testapp-production-v001']
		summaries.test[0].serverGroups.collect {it.instances} == [[] as Set]

		details.test.size() == 1
		details.test[0].name == 'testapp-production'
		details.test[0].accountName == 'test'
		details.test[0].serverGroups.collect {it.name} == ['testapp-production-v001']
		details.test[0].serverGroups.collect {it.instances.collect{"${it.name} is ${it.healthState}"}} == [["testapp-production-v001(0) is Up"]]

		1 * client.spaces >> {
			[
					new CloudSpace(
							mapToMeta([guid: uuid1, created: 1L]),
							"test",
							new CloudOrganization(
									mapToMeta([guid: uuid2, created: 2L]),
									"spinnaker"))
			]
		}
		1 * client.services >> { [new CloudService(mapToMeta([guid: uuid3, created: 3L]), 'spinnaker-redis')] }
		1 * client.domainsForOrg >> { [new CloudDomain(null, 'cfapps.io', null)] }
		1 * client.getRoutes('cfapps.io') >> {
			[new CloudRoute(null, 'my-cool-test-app', new CloudDomain(null, 'cfapps.io', null), 1)]
		}
		1 * client.applications >> {
			[
					buildNativeApplication([
							name     : 'testapp-production-v001',
							state    : CloudApplication.AppState.STARTED.toString(),
							instances: 1,
							services : ['spinnaker-redis'],
							memory   : 1024,
							env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
							meta     : [
									guid   : uuid2,
									created: 5L
							],
							space    : [
									meta        : [
											guid   : uuid3,
											created: 6L
									],
									name        : 'test',
									organization: [
											meta: [
													guid   : uuid1,
													created: 7L
											],
											name: 'spinnaker'
									]
							]
					])
			]
		}
		1 * client.getApplicationInstances(_) >> {
			new InstancesInfo([
					[since: 1L, index: 0, state: InstanceState.RUNNING.toString()]
			])
		}

		0 * client._

	}

	def "should serve up first 'since' time as createdTime when instances are in proper order"() {
		when:
		cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

		def serverGroup = clusterProvider.getServerGroup('test', 'spinnaker', 'testapp-production-v001')

		then:
		serverGroup.name == 'testapp-production-v001'
		serverGroup.createdTime == 1000L
		serverGroup.instanceCounts.up == 2

		1 * client.spaces >> {
			[
					new CloudSpace(
							mapToMeta([guid: uuid1, created: 1L]),
							"test",
							new CloudOrganization(
									mapToMeta([guid: uuid2, created: 2L]),
									"spinnaker"))
			]
		}
		1 * client.services >> { [new CloudService(mapToMeta([guid: uuid3, created: 3L]), 'spinnaker-redis')] }
		1 * client.domainsForOrg >> { [new CloudDomain(null, 'cfapps.io', null)] }
		1 * client.getRoutes('cfapps.io') >> {
			[new CloudRoute(null, 'my-cool-test-app', new CloudDomain(null, 'cfapps.io', null), 1)]
		}
		1 * client.applications >> {
			[
					buildNativeApplication([
							name     : 'testapp-production-v001',
							state    : CloudApplication.AppState.STARTED.toString(),
							instances: 1,
							services : ['spinnaker-redis'],
							memory   : 1024,
							env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
							meta     : [
									guid   : uuid2,
									created: 5L
							],
							space    : [
									meta        : [
											guid   : uuid3,
											created: 6L
									],
									name        : 'test',
									organization: [
											meta: [
													guid   : uuid1,
													created: 7L
											],
											name: 'spinnaker'
									]
							]
					])
			]
		}
		1 * client.getApplicationInstances(_) >> {
			new InstancesInfo([
					[since: 1L, index: 0, state: InstanceState.RUNNING.toString()],
					[since: 2L, index: 1, state: InstanceState.RUNNING.toString()]
			])
		}

		0 * client._
	}

	def "should serve up oldest 'since' time as createdTime when instances are out of order order"() {
		when:
		cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

		def serverGroup = clusterProvider.getServerGroup('test', 'spinnaker', 'testapp-production-v001')

		then:
		serverGroup.name == 'testapp-production-v001'
		serverGroup.createdTime == 1000000000L
		serverGroup.instanceCounts.up == 2

		1 * client.spaces >> {
			[
					new CloudSpace(
							mapToMeta([guid: uuid1, created: 1L]),
							"test",
							new CloudOrganization(
									mapToMeta([guid: uuid2, created: 2L]),
									"spinnaker"))
			]
		}
		1 * client.services >> { [new CloudService(mapToMeta([guid: uuid3, created: 3L]), 'spinnaker-redis')] }
		1 * client.domainsForOrg >> { [new CloudDomain(null, 'cfapps.io', null)] }
		1 * client.getRoutes('cfapps.io') >> {
			[new CloudRoute(null, 'my-cool-test-app', new CloudDomain(null, 'cfapps.io', null), 1)]
		}
		1 * client.applications >> {
			[
					buildNativeApplication([
							name     : 'testapp-production-v001',
							state    : CloudApplication.AppState.STARTED.toString(),
							instances: 2,
							services : ['spinnaker-redis'],
							memory   : 1024,
							env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
							meta     : [
									guid   : uuid2,
									created: 5L
							],
							space    : [
									meta        : [
											guid   : uuid3,
											created: 6L
									],
									name        : 'test',
									organization: [
											meta: [
													guid   : uuid1,
													created: 7L
											],
											name: 'spinnaker'
									]
							]
					])
			]
		}
		1 * client.getApplicationInstances(_) >> {
			new InstancesInfo([
					[since: 2000000L, index: 1, state: InstanceState.RUNNING.toString()],
					[since: 1000000L, index: 0, state: InstanceState.RUNNING.toString()]
			])
		}

		0 * client._
	}

}
