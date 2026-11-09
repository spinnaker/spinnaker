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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeServiceDeploymentDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ContinueServiceDeploymentRequest
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookAction
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookDetail
import software.amazon.awssdk.services.ecs.model.DeploymentLifecycleHookStatus
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsRequest
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsResponse
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.ServiceDeployment
import software.amazon.awssdk.services.ecs.model.StopServiceDeploymentStopType
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.StopServiceDeploymentRequest
import spock.lang.Specification
import spock.lang.Unroll

class EcsNativeServiceDeploymentAtomicOperationSpec extends Specification {

  static final String SERVICE_ARN = 'arn:aws:ecs:us-west-2:123456789012:service/cluster/service'
  static final String CLUSTER_ARN = 'arn:aws:ecs:us-west-2:123456789012:cluster/cluster'
  static final String OTHER_SERVICE_ARN = 'arn:aws:ecs:us-west-2:123456789012:service/cluster/unowned'
  static final String OTHER_CLUSTER_ARN = 'arn:aws:ecs:us-west-2:123456789012:cluster/other'

  def 'stops only the exact requested service deployment ARN'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeStopServiceDeploymentAtomicOperation(description([:])), ecs)

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments({ DescribeServiceDeploymentsRequest request ->
      request.serviceDeploymentArns() == ['arn:deployment:7']
    } as DescribeServiceDeploymentsRequest) >> deployments(deployment(SERVICE_ARN, CLUSTER_ARN).build())
    1 * ecs.stopServiceDeployment({ StopServiceDeploymentRequest request ->
      request.serviceDeploymentArn() == 'arn:deployment:7' &&
        request.stopType() == StopServiceDeploymentStopType.ROLLBACK
    } as StopServiceDeploymentRequest)
  }

  @Unroll
  def 'continue sends the awaiting hook id and action #expectedAction (lifecycleAction=#lifecycleAction)'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeContinueServiceDeploymentAtomicOperation(
      description(lifecycleAction: lifecycleAction)), ecs)

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments(_) >> deployments(deployment(SERVICE_ARN, CLUSTER_ARN)
      .lifecycleHookDetails(
        DeploymentLifecycleHookDetail.builder().hookId('done-hook').status(DeploymentLifecycleHookStatus.SUCCEEDED).build(),
        DeploymentLifecycleHookDetail.builder().hookId('pause-hook').status(DeploymentLifecycleHookStatus.AWAITING_ACTION).build())
      .build())
    1 * ecs.continueServiceDeployment({ ContinueServiceDeploymentRequest request ->
      request.serviceDeploymentArn() == 'arn:deployment:7' &&
        request.hookId() == 'pause-hook' &&
        request.action() == expectedAction
    } as ContinueServiceDeploymentRequest)

    where:
    lifecycleAction | expectedAction
    null            | DeploymentLifecycleHookAction.CONTINUE
    'continue'      | DeploymentLifecycleHookAction.CONTINUE
    'ROLLBACK'      | DeploymentLifecycleHookAction.ROLLBACK
  }

  def 'continue fails closed when no hook awaits action'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeContinueServiceDeploymentAtomicOperation(description([:])), ecs)

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments(_) >> deployments(deployment(SERVICE_ARN, CLUSTER_ARN).build())
    def e = thrown(IllegalStateException)
    e.message.contains('no lifecycle hook awaiting action')
    0 * ecs.continueServiceDeployment(_)
  }

  @Unroll
  def '#operationType refuses a deployment ARN that belongs to #reason'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(factory(description([:])), ecs)

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments(_) >> deployments(deployment(deploymentServiceArn, deploymentClusterArn)
      .lifecycleHookDetails(
        DeploymentLifecycleHookDetail.builder().hookId('pause-hook').status(DeploymentLifecycleHookStatus.AWAITING_ACTION).build())
      .build())
    def e = thrown(IllegalStateException)
    e.message.contains('does not belong to ecs-native service service')
    0 * ecs.stopServiceDeployment(_)
    0 * ecs.continueServiceDeployment(_)

    where:
    operationType | reason                  | deploymentServiceArn | deploymentClusterArn | factory
    'stop'        | 'another service'       | OTHER_SERVICE_ARN    | CLUSTER_ARN          | { d -> new EcsNativeStopServiceDeploymentAtomicOperation(d) }
    'continue'    | 'another service'       | OTHER_SERVICE_ARN    | CLUSTER_ARN          | { d -> new EcsNativeContinueServiceDeploymentAtomicOperation(d) }
    'stop'        | 'another cluster'       | SERVICE_ARN          | OTHER_CLUSTER_ARN    | { d -> new EcsNativeStopServiceDeploymentAtomicOperation(d) }
    'continue'    | 'an unknown service'    | null                 | CLUSTER_ARN          | { d -> new EcsNativeContinueServiceDeploymentAtomicOperation(d) }
  }

  def 'refuses to act when the requested deployment ARN is not returned by ECS'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeStopServiceDeploymentAtomicOperation(description([:])), ecs)

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments(_) >> DescribeServiceDeploymentsResponse.builder().build()
    def e = thrown(IllegalStateException)
    e.message.contains('was not found')
    0 * ecs.stopServiceDeployment(_)
  }

  def 'refuses to act when the owned service has no ARN to compare against'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(
      new EcsNativeStopServiceDeploymentAtomicOperation(description([:])), ecs,
      Service.builder().serviceName('service').tags(EcsNativeServiceTag.tag()).build())

    when:
    operation.operate([])

    then:
    1 * ecs.describeServiceDeployments(_) >> deployments(deployment(SERVICE_ARN, CLUSTER_ARN).build())
    thrown(IllegalStateException)
    0 * ecs.stopServiceDeployment(_)
  }

  def 'refuses to act on a deployment of an untagged service before describing it'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(
      new EcsNativeStopServiceDeploymentAtomicOperation(description([:])), ecs,
      Service.builder().serviceName('service').serviceArn(SERVICE_ARN).build())

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalStateException)
    e.message.contains('not marked as owned by ecs-native')
    0 * ecs.describeServiceDeployments(_)
    0 * ecs.stopServiceDeployment(_)
  }

  def 'polls DescribeServices until ECS attaches the new service deployment'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeContinueServiceDeploymentAtomicOperation(description([:])), ecs)
    operation.serviceDeploymentPollMillis = 0
    def bare = Service.builder().serviceName('service').build()
    def attached = Service.builder().serviceName('service').currentServiceDeployment('arn:deployment:9').build()

    when:
    def arn = operation.resolveCurrentServiceDeployment(bare, 'cluster-arn')

    then:
    2 * ecs.describeServices(_) >>> [
      DescribeServicesResponse.builder().services(bare).build(),
      DescribeServicesResponse.builder().services(attached).build()]
    arn == 'arn:deployment:9'
  }

  def 'returns the deployment ARN straight from the service when present'() {
    given:
    def ecs = Mock(EcsClient)
    def operation = lifecycleOperation(new EcsNativeContinueServiceDeploymentAtomicOperation(description([:])), ecs)

    expect:
    operation.resolveCurrentServiceDeployment(
      Service.builder().serviceName('service').currentServiceDeployment('arn:deployment:1').build(), 'cluster-arn') == 'arn:deployment:1'
  }

  def 'rejects a lifecycle request without the pinned ARN before an AWS write'() {
    given:
    def operation = new EcsNativeStopServiceDeploymentAtomicOperation(new EcsNativeServiceDeploymentDescription(
      credentials: TestCredential.named('test', [:]),
      region: 'us-west-2',
      ecsClusterName: 'cluster-arn',
      serverGroupName: 'service'
    ))

    when:
    operation.operate([])

    then:
    thrown(IllegalArgumentException)
    0 * _
  }

  private static EcsNativeServiceDeploymentDescription description(Map overrides) {
    new EcsNativeServiceDeploymentDescription([
      credentials: TestCredential.named('test', [:]),
      region: 'us-west-2',
      ecsClusterName: 'cluster-arn',
      serverGroupName: 'service',
      ecsNativeExpectedServiceDeploymentArn: 'arn:deployment:7'
    ] + overrides)
  }

  private static ServiceDeployment.Builder deployment(String serviceArn, String clusterArn) {
    ServiceDeployment.builder()
      .serviceDeploymentArn('arn:deployment:7')
      .serviceArn(serviceArn)
      .clusterArn(clusterArn)
  }

  private static DescribeServiceDeploymentsResponse deployments(ServiceDeployment deployment) {
    DescribeServiceDeploymentsResponse.builder().serviceDeployments(deployment).build()
  }

  private static Service ownedService() {
    Service.builder()
      .serviceName('service')
      .serviceArn(SERVICE_ARN)
      .clusterArn(CLUSTER_ARN)
      .tags(EcsNativeServiceTag.tag())
      .build()
  }

  private lifecycleOperation(AbstractEcsNativeServiceDeploymentAtomicOperation operation, EcsClient ecs,
                             Service service = ownedService()) {
    def provider = Mock(AmazonClientProvider)
    operation.amazonClientProvider = provider
    provider.getAmazonEcsV2(_, 'us-west-2') >> ecs
    ecs.describeServices(_) >> DescribeServicesResponse.builder().services(service).build()
    operation
  }
}
