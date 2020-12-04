/*
 * Copyright 2020 Expedia, Inc.
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
 *
 */


package com.netflix.spinnaker.clouddriver.ecs.names


import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.*
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import spock.lang.Specification

class EcsServerGroupNameResolverSpec extends Specification {
  def ecsClient = Mock(AmazonECS)

  def ecsClusterName = 'default'
  def region = 'us-west-1'

  void setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  void "should handle only tagged services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsTagNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("another-tagged-service", "tagged-service")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["another-tagged-service", "tagged-service"]
      request.include == ["TAGS"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "another-tagged-service", createdAt: new Date(1), status: "ACTIVE",
        tags: [
          new Tag(key: EcsTagNamer.CLUSTER, value: "application-stack-details"),
          new Tag(key: EcsTagNamer.APPLICATION, value: "application"),
          new Tag(key: EcsTagNamer.STACK, value: "stack"),
          new Tag(key: EcsTagNamer.DETAIL, value: "details"),
          new Tag(key: EcsTagNamer.SEQUENCE, value: 1)
        ]),
      new Service(serviceName: "tagged-service", createdAt: new Date(1), status: "ACTIVE",
        tags: [
          new Tag(key: EcsTagNamer.CLUSTER, value: "application-stack-details"),
          new Tag(key: EcsTagNamer.APPLICATION, value: "application"),
          new Tag(key: EcsTagNamer.STACK, value: "stack"),
          new Tag(key: EcsTagNamer.DETAIL, value: "details"),
          new Tag(key: EcsTagNamer.SEQUENCE, value: 2)
        ])
    )
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v003", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should handle mix of tagged services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsTagNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "tagged-service")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "tagged-service"]
      request.include == ["TAGS"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "tagged-service", createdAt: new Date(1), status: "ACTIVE",
        tags: [
          new Tag(key: EcsTagNamer.CLUSTER, value: "application-stack-details"),
          new Tag(key: EcsTagNamer.APPLICATION, value: "application"),
          new Tag(key: EcsTagNamer.STACK, value: "stack"),
          new Tag(key: EcsTagNamer.DETAIL, value: "details"),
          new Tag(key: EcsTagNamer.SEQUENCE, value: 2)
        ])
    )
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v003", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should generate new name from first sequence"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns([])
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v000"]
    }) >> new DescribeServicesResult().withServices([])

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v000"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should handle sequence roll over"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v999")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v999"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v999", createdAt: new Date(1), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v000"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v000", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v000"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should resolve task definition family name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
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
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v002"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should resolve task definition container name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
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
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should skip names that already exist as ECS services in active and draining state"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
      request.include == ["TAGS"]
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
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v005"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should not skip names for inactive ECS services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
      request.include == ["TAGS"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v003"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v003", createdAt: new Date(3), status: "INACTIVE"))

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should give up trying to find a name if all services are draining"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns("application-stack-details-v001", "application-stack-details-v002")
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v001", "application-stack-details-v002"]
      request.include == ["TAGS"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v002", createdAt: new Date(2), status: "ACTIVE"))
    ecsClient.describeServices(_) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "blah-blah", createdAt: new Date(1), status: "DRAINING"))

    when:
    resolver.resolveNextName('application', 'stack', 'details')

    then:
    thrown(IllegalArgumentException)
  }

  void "should generate name with null details"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns([])
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-v000"]
    }) >> new DescribeServicesResult().withServices([])

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', null)

    then:
    nextServerGroupName.getServiceName() == "application-stack-v000"
    nextServerGroupName.getFamilyName() == "application-stack"
  }

  void "should generate name with null stack"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns([])
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application--details-v000"]
    }) >> new DescribeServicesResult().withServices([])

    when:
    def nextServerGroupName = resolver.resolveNextName('application', null, 'details')

    then:
    nextServerGroupName.getServiceName() == "application--details-v000"
    nextServerGroupName.getFamilyName() == "application--details"
  }

  void "should generate name with null details and stack"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns([])
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-v000"]
    }) >> new DescribeServicesResult().withServices([])

    when:
    def nextServerGroupName = resolver.resolveNextName('application', null, null)

    then:
    nextServerGroupName.getServiceName() == "application-v000"
    nextServerGroupName.getFamilyName() == "application"
  }

  void "should handle find slot after max with gaps"() {
    given:
    def serviceArns = ["application-stack-details-v001", "application-stack-details-v003",
                       "application-stack-details-v004", "application-stack-details-v005",
                       "application-stack-details-v006"]
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> new ListServicesResult().withServiceArns(serviceArns)
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == serviceArns
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "application-stack-details-v001", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v003", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v004", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v005", createdAt: new Date(1), status: "ACTIVE"),
      new Service(serviceName: "application-stack-details-v006", createdAt: new Date(1), status: "ACTIVE")
    )
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster == ecsClusterName
      request.services == ["application-stack-details-v007"]
    }) >> new DescribeServicesResult().withFailures(
      new Failure(arn: "application-stack-details-v007", reason: "MISSING")
    )

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v007"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

}
