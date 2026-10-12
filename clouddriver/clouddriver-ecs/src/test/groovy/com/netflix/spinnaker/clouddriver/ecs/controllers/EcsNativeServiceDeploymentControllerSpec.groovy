/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import java.time.Instant
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsRequest
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsResponse
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsRequest
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsResponse
import software.amazon.awssdk.services.ecs.model.ServiceDeployment
import software.amazon.awssdk.services.ecs.model.ServiceRevision
import software.amazon.awssdk.services.ecs.model.ServiceRevisionSummary
import spock.lang.Unroll

class EcsNativeServiceDeploymentControllerSpec extends Specification {

  static final String DEPLOYMENT_ARN = 'arn:aws:ecs:us-west-2:123:service-deployment/myapp/1'
  static final String REVISION_ARN = 'arn:aws:ecs:us-west-2:123:service-revision/myapp/2'
  static final String SERVICE_ARN = 'arn:aws:ecs:us-west-2:123:service/cluster/myapp'
  static final String CLUSTER_ARN = 'arn:aws:ecs:us-west-2:123:cluster/cluster'

  def credentialsRepository = Mock(CredentialsRepository)
  def amazonClientProvider = Mock(AmazonClientProvider)
  def serviceCacheClient = Mock(ServiceCacheClient)
  def ecs = Mock(EcsClient)

  @Subject
  def controller = new EcsNativeServiceDeploymentController(
    credentialsRepository, amazonClientProvider, serviceCacheClient)

  def 'returns 400 when deployment identity is missing'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', null)

    then:
    response.statusCode == HttpStatus.BAD_REQUEST
  }

  def 'returns 400 when the account is not an ECS account'() {
    given:
    credentialsRepository.getOne('test') >> null

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', 'arn:aws:ecs:us-west-2:123:service-deployment/myapp/1')

    then:
    response.statusCode == HttpStatus.BAD_REQUEST
  }

  def 'resolves the account case-insensitively when the exact case is not registered'() {
    given:
    credentialsRepository.getOne('FGATE-PS-PRODUCTION') >> null
    credentialsRepository.getAll() >> [Mock(NetflixECSCredentials) { getName() >> 'fgate-ps-production' }]
    serviceCacheClient.get(_) >> null

    when:
    def response = controller.getDeploymentStatus(
      'FGATE-PS-PRODUCTION', 'eu-central-1', 'myapp', 'arn:aws:ecs:us-west-2:123:service-deployment/myapp/1')

    then:
    response.statusCode == HttpStatus.NOT_FOUND
  }

  def 'returns 404 when the service is not in the cache'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> null

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', 'arn:aws:ecs:us-west-2:123:service-deployment/myapp/1')

    then:
    response.statusCode == HttpStatus.NOT_FOUND
    0 * credentialsRepository.getAll()
  }

  def 'returns 404 while ECS has not exposed the expected service deployment'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> cachedService()
    amazonClientProvider.getAmazonEcsV2(_, 'us-west-2') >> ecs

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', DEPLOYMENT_ARN)

    then:
    1 * ecs.describeServiceDeployments(_) >> DescribeServiceDeploymentsResponse.builder().build()
    0 * ecs.describeServiceRevisions(_)
    response.statusCode == HttpStatus.NOT_FOUND
  }

  def 'describes only the expected deployment and its target revision, never the history'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> cachedService()
    amazonClientProvider.getAmazonEcsV2(_, 'us-west-2') >> ecs

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', DEPLOYMENT_ARN)

    then:
    0 * ecs.listServiceDeployments(_)
    1 * ecs.describeServiceDeployments({ DescribeServiceDeploymentsRequest request ->
      request.serviceDeploymentArns() == [DEPLOYMENT_ARN]
    } as DescribeServiceDeploymentsRequest) >> DescribeServiceDeploymentsResponse.builder()
      .serviceDeployments(deployment(SERVICE_ARN, CLUSTER_ARN).build())
      .build()
    1 * ecs.describeServiceRevisions({ DescribeServiceRevisionsRequest request ->
      request.serviceRevisionArns() == [REVISION_ARN]
    } as DescribeServiceRevisionsRequest) >> DescribeServiceRevisionsResponse.builder()
      .serviceRevisions(ServiceRevision.builder().serviceRevisionArn(REVISION_ARN).taskDefinition('task-def-arn').build())
      .build()
    response.statusCode == HttpStatus.OK
  }

  @Unroll
  def 'returns 409 when the expected deployment belongs to #reason'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> cachedService()
    amazonClientProvider.getAmazonEcsV2(_, 'us-west-2') >> ecs
    ecs.describeServiceDeployments(_) >> DescribeServiceDeploymentsResponse.builder()
      .serviceDeployments(deployment(serviceArn, clusterArn).build())
      .build()

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', DEPLOYMENT_ARN)

    then:
    response.statusCode == HttpStatus.CONFLICT
    0 * ecs.describeServiceRevisions(_)

    where:
    reason            | serviceArn                                              | clusterArn
    'another service' | 'arn:aws:ecs:us-west-2:123:service/cluster/other'       | CLUSTER_ARN
    'another cluster' | SERVICE_ARN                                             | 'arn:aws:ecs:us-west-2:123:cluster/other'
    'no service'      | null                                                    | CLUSTER_ARN
  }

  def 'maps the matching service deployment lifecycle state onto the response'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> cachedService()
    amazonClientProvider.getAmazonEcsV2(_, 'us-west-2') >> ecs

    def revision = ServiceRevision.builder()
      .serviceRevisionArn(REVISION_ARN)
      .taskDefinition('task-def-arn')
      .build()
    def deployment = deployment(SERVICE_ARN, CLUSTER_ARN)
      .status('IN_PROGRESS')
      .statusReason('ECS deployment is in progress.')
      .lifecycleStage('SCALE_UP')
      .createdAt(Instant.parse('2026-09-24T10:00:00Z'))
      .updatedAt(Instant.parse('2026-09-24T10:01:00Z'))
      .build()
    ecs.describeServiceDeployments(_) >> DescribeServiceDeploymentsResponse.builder()
      .serviceDeployments(deployment)
      .build()
    ecs.describeServiceRevisions(_) >> DescribeServiceRevisionsResponse.builder()
      .serviceRevisions(revision)
      .build()

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', DEPLOYMENT_ARN)

    then:
    response.statusCode == HttpStatus.OK
    with(response.body) {
      serviceName == 'myapp'
      clusterArn == CLUSTER_ARN
      serviceDeploymentArn == DEPLOYMENT_ARN
      targetServiceRevisionArn == REVISION_ARN
      targetTaskDefinition == 'task-def-arn'
      status == 'IN_PROGRESS'
      lifecycleStage == 'SCALE_UP'
      rolloutState == 'IN_PROGRESS'
      statusReason == 'ECS deployment is in progress.'
    }
  }

  def 'does not return a different service deployment identity'() {
    given:
    credentialsRepository.getOne('test') >> Mock(NetflixECSCredentials) { getName() >> 'test' }
    serviceCacheClient.get(_) >> cachedService()
    amazonClientProvider.getAmazonEcsV2(_, 'us-west-2') >> ecs
    ecs.describeServiceDeployments(_) >> DescribeServiceDeploymentsResponse.builder()
      .serviceDeployments(ServiceDeployment.builder()
        .serviceDeploymentArn('arn:aws:ecs:us-west-2:123:service-deployment/myapp/rollback')
        .serviceArn(SERVICE_ARN)
        .clusterArn(CLUSTER_ARN)
        .status('SUCCESSFUL')
        .targetServiceRevision(ServiceRevisionSummary.builder().arn(REVISION_ARN).build())
        .build())
      .build()

    when:
    def response = controller.getDeploymentStatus('test', 'us-west-2', 'myapp', 'new-service-deployment-arn')

    then:
    response.statusCode == HttpStatus.NOT_FOUND
  }

  private static Service cachedService() {
    new Service(serviceName: 'myapp', serviceArn: SERVICE_ARN, clusterArn: CLUSTER_ARN)
  }

  private static ServiceDeployment.Builder deployment(String serviceArn, String clusterArn) {
    ServiceDeployment.builder()
      .serviceDeploymentArn(DEPLOYMENT_ARN)
      .serviceArn(serviceArn)
      .clusterArn(clusterArn)
      .targetServiceRevision(ServiceRevisionSummary.builder().arn(REVISION_ARN).build())
  }
}
