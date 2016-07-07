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
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.*
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.buildNativeApplication
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.mapToMeta

class CloudFoundryInstanceProviderSpec extends Specification {

	private static final ACCOUNT_NAME = "test"

	CloudFoundryInstanceProvider instanceProvider

	CloudFoundryClient client

	ClusterCachingAgent cachingAgent

	ProviderRegistry registry

	@Shared
	CloudFoundryAccountCredentials cloudFoundryCredentials = TestCredential.named(ACCOUNT_NAME,
			[
					api: 'https://api.example.com',
					org: 'spinnaker',
					space: 'staging'
			])

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

		def credentialsRepo = new MapBackedAccountCredentialsRepository()
		def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
		credentialsRepo.save(ACCOUNT_NAME, cloudFoundryCredentials)


		instanceProvider = new CloudFoundryInstanceProvider(registry.getProviderCache(CloudFoundryProvider.PROVIDER_NAME), credentialsProvider)
	}

	def "should handle an empty cache"() {
		when:
		def instance = instanceProvider.getInstance(ACCOUNT_NAME, 'spinnaker', 'testapp-production-v001(0)')

		then:
		instance == null
	}

	def "should handle a cache miss"() {
		expect:
		instanceProvider.getInstance('foo', 'foo', 'foo') == null
	}

	def "should handle a basic instance lookup"() {
		when:
		cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

		def instance = instanceProvider.getInstance(ACCOUNT_NAME, 'spinnaker', 'testapp-production-v001(0)')

		then:
		instance.name == 'testapp-production-v001(0)'
		instance.healthState == HealthState.Up
		instance.health.size() == 1
		instance.health[0].state == HealthState.Up.toString()
		instance.health[0].zone == 'test'
		instance.health[0].type == 'serverGroup'
		instance.health[0].description == 'Is this CF server group running?'
		instance.nativeApplication.name == 'testapp-production-v001'
		instance.nativeInstance.index == 0
		instance.nativeInstance.since.time == 1000L

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
									name        : ACCOUNT_NAME,
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

	def "console output only returns null for now"() {
		expect:
		instanceProvider.getConsoleOutput(ACCOUNT_NAME, 'spinnaker', 'testapp-production-v001(0)') == null
	}

}
