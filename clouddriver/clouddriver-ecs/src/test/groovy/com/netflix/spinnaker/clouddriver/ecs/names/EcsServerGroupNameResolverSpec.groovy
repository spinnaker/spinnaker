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


import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import spock.lang.Specification

import java.time.Instant

class EcsServerGroupNameResolverSpec extends Specification {
  def ecsClient = Mock(EcsClient)

  def ecsClusterName = 'default'
  def region = 'us-west-1'

  void setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  void "should handle only tagged services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsTagNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("another-tagged-service", "tagged-service").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["another-tagged-service", "tagged-service"]
      request.includeAsStrings() == ["TAGS"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("another-tagged-service").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE")
        .tags(
          Tag.builder().key(EcsTagNamer.CLUSTER).value("application-stack-details").build(),
          Tag.builder().key(EcsTagNamer.APPLICATION).value("application").build(),
          Tag.builder().key(EcsTagNamer.STACK).value("stack").build(),
          Tag.builder().key(EcsTagNamer.DETAIL).value("details").build(),
          Tag.builder().key(EcsTagNamer.SEQUENCE).value("1").build()
        ).build(),
      Service.builder().serviceName("tagged-service").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE")
        .tags(
          Tag.builder().key(EcsTagNamer.CLUSTER).value("application-stack-details").build(),
          Tag.builder().key(EcsTagNamer.APPLICATION).value("application").build(),
          Tag.builder().key(EcsTagNamer.STACK).value("stack").build(),
          Tag.builder().key(EcsTagNamer.DETAIL).value("details").build(),
          Tag.builder().key(EcsTagNamer.SEQUENCE).value("2").build()
        ).build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v003"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v003").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should handle mix of tagged services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsTagNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001", "tagged-service").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001", "tagged-service"]
      request.includeAsStrings() == ["TAGS"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("tagged-service").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE")
        .tags(
          Tag.builder().key(EcsTagNamer.CLUSTER).value("application-stack-details").build(),
          Tag.builder().key(EcsTagNamer.APPLICATION).value("application").build(),
          Tag.builder().key(EcsTagNamer.STACK).value("stack").build(),
          Tag.builder().key(EcsTagNamer.DETAIL).value("details").build(),
          Tag.builder().key(EcsTagNamer.SEQUENCE).value("2").build()
        ).build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v003"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v003").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should generate new name from first sequence"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns([]).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v000"]
    }) >> DescribeServicesResponse.builder().services([]).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v000"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should handle sequence roll over"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v999").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v999"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v999").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v000"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v000").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v000"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should resolve task definition family name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v002"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v002").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v002"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should resolve task definition container name from service group name"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001", "application-stack-details-v002").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001", "application-stack-details-v002"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v002").createdAt(Instant.ofEpochMilli(2)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v003"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v003").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should skip names that already exist as ECS services in active and draining state"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001", "application-stack-details-v002").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001", "application-stack-details-v002"]
      request.includeAsStrings() == ["TAGS"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v002").createdAt(Instant.ofEpochMilli(2)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v003"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v003").createdAt(Instant.ofEpochMilli(3)).status("DRAINING").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v004"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v004").createdAt(Instant.ofEpochMilli(3)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v005"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v005").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v005"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should not skip names for inactive ECS services"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001", "application-stack-details-v002").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001", "application-stack-details-v002"]
      request.includeAsStrings() == ["TAGS"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v002").createdAt(Instant.ofEpochMilli(2)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v003"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v003").createdAt(Instant.ofEpochMilli(3)).status("INACTIVE").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v003"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should give up trying to find a name if all services are draining"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details-v001", "application-stack-details-v002").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v001", "application-stack-details-v002"]
      request.includeAsStrings() == ["TAGS"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v002").createdAt(Instant.ofEpochMilli(2)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices(_) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("blah-blah").createdAt(Instant.ofEpochMilli(1)).status("DRAINING").build()
    ).build()

    when:
    resolver.resolveNextName('application', 'stack', 'details')

    then:
    thrown(IllegalArgumentException)
  }

  void "should generate name with null details"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns([]).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-v000"]
    }) >> DescribeServicesResponse.builder().services([]).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', null)

    then:
    nextServerGroupName.getServiceName() == "application-stack-v000"
    nextServerGroupName.getFamilyName() == "application-stack"
  }

  void "should generate name with null stack"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns([]).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application--details-v000"]
    }) >> DescribeServicesResponse.builder().services([]).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', null, 'details')

    then:
    nextServerGroupName.getServiceName() == "application--details-v000"
    nextServerGroupName.getFamilyName() == "application--details"
  }

  void "should skip name if stack name is null and the existing one is empty"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns(
      "application--details-v001",
      "application--details-v002"
    ).build()

    and: 'two existing and active services'
    ecsClient.describeServices({ DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application--details-v001", "application--details-v002"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application--details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application--details-v002").createdAt(Instant.ofEpochMilli(2)).status("ACTIVE").build()
    ).build()

    and: 'one missing service'
    ecsClient.describeServices({ DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application--details-v003"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application--details-v003").reason("MISSING").build()
    ).build()


    when: 'stack has an empty value on resolving name'
    def nextServerGroupName = resolver.resolveNextName('application', '', 'details')

    then: 'it will have the same result as if it was null'
    nextServerGroupName.getServiceName() == "application--details-v003"
    nextServerGroupName.getFamilyName() == "application--details"

    // If this is called it means `resolveNextName` failed to add the taken sequences (1 and 2)
    0 * ecsClient.describeServices({ DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application--details-v000"]
    })
  }

  void "should generate name with null details and stack"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns([]).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-v000"]
    }) >> DescribeServicesResponse.builder().services([]).build()

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
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns(serviceArns).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == serviceArns
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details-v001").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v003").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v004").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v005").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build(),
      Service.builder().serviceName("application-stack-details-v006").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v007"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v007").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v007"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

  void "should resolve task definition family name from service group name with no sequence"() {
    given:
    def resolver = new EcsServerGroupNameResolver(ecsClusterName, ecsClient, region, new EcsDefaultNamer())
    ecsClient.listServices(_) >> ListServicesResponse.builder().serviceArns("application-stack-details").build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details"]
    }) >> DescribeServicesResponse.builder().services(
      Service.builder().serviceName("application-stack-details").createdAt(Instant.ofEpochMilli(1)).status("ACTIVE").build()
    ).build()
    ecsClient.describeServices({DescribeServicesRequest request ->
      request.cluster() == ecsClusterName
      request.services() == ["application-stack-details-v000"]
    }) >> DescribeServicesResponse.builder().failures(
      Failure.builder().arn("application-stack-details-v000").reason("MISSING").build()
    ).build()

    when:
    def nextServerGroupName = resolver.resolveNextName('application', 'stack', 'details')

    then:
    nextServerGroupName.getServiceName() == "application-stack-details-v000"
    nextServerGroupName.getFamilyName() == "application-stack-details"
  }

}
