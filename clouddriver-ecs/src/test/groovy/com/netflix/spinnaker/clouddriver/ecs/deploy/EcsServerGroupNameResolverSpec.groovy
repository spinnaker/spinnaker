/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */


package com.netflix.spinnaker.clouddriver.ecs.deploy


import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.*
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import spock.lang.Specification

class EcsServerGroupNameResolverSpec extends Specification {
  def ecsClient = Mock(AmazonECS)

  def ecsClusterName = 'default'
  def region = 'us-west-1'

  void setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  void "should build TakenSlots based on existing ECS services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)

    when:
    def takenSlots = resolver.getTakenSlots('application-stack-details')

    then:
    1 * ecsClient.listServices({ListServicesRequest request ->
      request.cluster == ecsClusterName
    }) >> new ListServicesResult().withServiceArns(
      "arn:aws:ecs:region:account-id:service/cluster-name/application-stack-details-v000",
      "arn:aws:ecs:region:account-id:service/cluster-name/hello-world-v000")
    1 * ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["arn:aws:ecs:region:account-id:service/cluster-name/application-stack-details-v000"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v000", createdAt: new Date(1), status: "ACTIVE"))

    takenSlots == [
        new AbstractServerGroupNameResolver.TakenSlot('application-stack-details-v000', 0, new Date(1))
    ]
  }

  void "should resolve task definition family name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v002"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v002", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)
    def familyName = EcsServerGroupNameResolver.getEcsFamilyName(nextServerGroupName)

    then:
    nextServerGroupName == "application-stack-details-v002".toString()
    familyName == "application-stack-details"
  }

  void "should resolve task definition container name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v003", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)
    def containerName = EcsServerGroupNameResolver.getEcsContainerName(nextServerGroupName)

    then:
    nextServerGroupName == "application-stack-details-v003".toString()
    containerName == "v003"
  }

  void "should skip names that already exist as ECS services in active and draining state"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v003", createdAt: new Date(3), status: "DRAINING"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v004"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v004", createdAt: new Date(3), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v005"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v005", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)
    def containerName = EcsServerGroupNameResolver.getEcsContainerName(nextServerGroupName)

    then:
    nextServerGroupName == "application-stack-details-v005".toString()
    containerName == "v005"
  }

  void "should not skip names for inactive ECS services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v003", createdAt: new Date(3), status: "INACTIVE"))

    when:
    def nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)
    def containerName = EcsServerGroupNameResolver.getEcsContainerName(nextServerGroupName)

    then:
    nextServerGroupName == "application-stack-details-v003".toString()
    containerName == "v003"
  }

  void "should give up trying to find a name if all services are draining"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region)
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices(_) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "blah-blah", createdAt: new Date(1), status: "DRAINING"))

    when:
    resolver.resolveNextServerGroupName('application', 'stack', 'details', false)

    then:
    thrown(IllegalArgumentException)
  }
}
