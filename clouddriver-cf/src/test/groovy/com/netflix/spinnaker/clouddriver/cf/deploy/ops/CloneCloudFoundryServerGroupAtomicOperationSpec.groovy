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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.deploy.converters.CloneCloudFoundryServerGroupAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.cf.deploy.handlers.CloudFoundryDeployHandler
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.CloudApplication
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class CloneCloudFoundryServerGroupAtomicOperationSpec extends Specification {

	private static final String ACCOUNT_NAME = "auto"
	private static final String PROJECT_NAME = "my_project"
	private static final String APPLICATION_NAME = "myapp"
	private static final String STACK_NAME = "dev"
	private static final String ANCESTOR_SERVER_GROUP_NAME = "$APPLICATION_NAME-$STACK_NAME-v000"
	private static final String NEW_SERVER_GROUP_NAME = "$APPLICATION_NAME-$STACK_NAME-v001"
	private static final String API = 'https://api.example.com'
	private static final String ORG = 'spinnaker'
	private static final String SPACE = 'staging'
	private static final int INSTANCES = 2
	private static final int MEMORY = 4096
	private static final int DISK = 2048

	private static final String REPOSITORY = 'http://artifactory.example.com'
	private static final String ARTIFACT = 'my-cool-app-0.1.0.jar'
	private static final String USERNAME = 'artifactory-user'
	private static final String PASSWORD = 'artifactory-password'

	@Shared
	ObjectMapper mapper = new ObjectMapper()

	@Shared
	CloneCloudFoundryServerGroupAtomicOperationConverter converter

	@Shared
	CloudFoundryAccountCredentials cloudFoundryCredentials = new CloudFoundryAccountCredentials(
			[
				name: ACCOUNT_NAME,
				api: API,
				org: ORG,
				space: SPACE,
				artifactUsername: USERNAME,
				artifactPassword: PASSWORD
			])

	CloudFoundryDeployHandler deployHandlerMock

	private def serverGroup

	Task task

	private def credentialsRepo


	def setup() {
		deployHandlerMock = Mock(CloudFoundryDeployHandler)

		task = new DefaultTask('test')
		TaskRepository.threadLocalTask.set(task)

		serverGroup = new CloudApplication(null, ANCESTOR_SERVER_GROUP_NAME)
		serverGroup.instances = INSTANCES
		serverGroup.diskQuota = DISK
		serverGroup.memory = MEMORY
		serverGroup.services = ['spinnaker-redis', 'cool-database']

		credentialsRepo = new MapBackedAccountCredentialsRepository()
		credentialsRepo.save(ACCOUNT_NAME, cloudFoundryCredentials)
	}

	def "operation builds description based on ancestor, overriding everything"() {
		setup:
		def description = new CloudFoundryDeployDescription(
				credentials: cloudFoundryCredentials,
				application: 'diffapp',
				stack: 'diffstack',
				space: 'diffspace',
				targetSize: 2,
				disk: 1024,
				memory: 2048,
				loadBalancers: "${APPLICATION_NAME}-${STACK_NAME}.example.com",
				services: ['another-redis', 'another-database'],
				envs: [something: 'else', overrided: 'variable'],
				repository: 'http://different.example.com',
				artifact: 'different-0.1.0.jar',
				username: 'different-username',
				password: 'different-password',
				source: [
					serverGroupName: ANCESTOR_SERVER_GROUP_NAME,
				],
		)

		def newDescription = description.clone()

		def clientFactory = Mock(CloudFoundryClientFactory)
		def cloudFoundryClient = Mock(CloudFoundryClient)

		def deploymentResult = new DeploymentResult(serverGroupNames: ["$ORG:$NEW_SERVER_GROUP_NAME"])
		@Subject def operation = new CloneCloudFoundryServerGroupAtomicOperation(description)
		operation.deployHandler = deployHandlerMock
		operation.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)

		when:
		operation.operate([])

		then:
		2 * deployHandlerMock.clientFactory >> clientFactory

		1 * clientFactory.createCloudFoundryClient(_, _) >> cloudFoundryClient

		1 * cloudFoundryClient.getApplication('myapp-dev-v000') >> serverGroup
		1 * cloudFoundryClient.getApplicationEnvironment('myapp-dev-v000') >> [
				'environment_json': [
						(CloudFoundryConstants.REPOSITORY): REPOSITORY,
						(CloudFoundryConstants.ARTIFACT)  : ARTIFACT,
						(CloudFoundryConstants.LOAD_BALANCERS): "${ANCESTOR_SERVER_GROUP_NAME}.cfapps.io,${APPLICATION_NAME}-${STACK_NAME}.cfapps.io",
						'foo'                             : 'bar',
				]
		]

		1 * deployHandlerMock.handle(newDescription, []) >> deploymentResult

		0 * _
	}

	def "operation builds description based on ancestor, overrides nothing"() {
		setup:
		def description = new CloudFoundryDeployDescription(source:
				[
					serverGroupName: ANCESTOR_SERVER_GROUP_NAME,
				],
				credentials: cloudFoundryCredentials)

		def newDescription = description.clone()
		newDescription.application = APPLICATION_NAME
		newDescription.stack = STACK_NAME
		newDescription.credentials = cloudFoundryCredentials
		newDescription.space = SPACE
		newDescription.targetSize = INSTANCES
		newDescription.memory = MEMORY
		newDescription.disk = DISK
		newDescription.repository = REPOSITORY
		newDescription.artifact = ARTIFACT
		newDescription.username = USERNAME
		newDescription.password = PASSWORD
		newDescription.envs = [foo: 'bar', boo: 'yah']
		newDescription.loadBalancers = "${APPLICATION_NAME}-${STACK_NAME}.cfapps.io"
		newDescription.services = ['spinnaker-redis', 'cool-database']

		def clientFactory = Mock(CloudFoundryClientFactory)
		def cloudFoundryClient = Mock(CloudFoundryClient)

		def deploymentResult = new DeploymentResult(serverGroupNames: ["$ORG:$NEW_SERVER_GROUP_NAME"])
		@Subject def operation = new CloneCloudFoundryServerGroupAtomicOperation(description)
		operation.deployHandler = deployHandlerMock
		operation.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)

		when:
		operation.operate([])

		then:
		2 * deployHandlerMock.clientFactory >> clientFactory

		1 * clientFactory.createCloudFoundryClient(_, _) >> cloudFoundryClient

		1 * cloudFoundryClient.getApplication('myapp-dev-v000') >> serverGroup
		1 * cloudFoundryClient.getApplicationEnvironment('myapp-dev-v000') >> [
			'environment_json': [
				(CloudFoundryConstants.REPOSITORY): REPOSITORY,
				(CloudFoundryConstants.ARTIFACT)  : ARTIFACT,
				(CloudFoundryConstants.LOAD_BALANCERS): "${ANCESTOR_SERVER_GROUP_NAME}.cfapps.io,${APPLICATION_NAME}-${STACK_NAME}.cfapps.io",
				foo                            : 'bar',
				boo                            : 'yah',
			]
		]

		1 * deployHandlerMock.handle(newDescription, []) >> deploymentResult

		0 * _
	}

}