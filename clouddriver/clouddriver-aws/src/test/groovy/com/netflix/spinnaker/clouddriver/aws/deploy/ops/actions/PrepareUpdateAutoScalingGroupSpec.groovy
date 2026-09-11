package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.ModifyServerGroupLaunchTemplateValidator
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PrepareUpdateAutoScalingGroupSpec extends Specification {
  @Shared
  ModifyServerGroupLaunchTemplateValidator validator

  def autoScalingGroupWithLt = AutoScalingGroup.builder()
    .autoScalingGroupName("test-v001")
    .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateName("lt-1").version("1").build())
    .build()

  def description = new ModifyServerGroupLaunchTemplateDescription(
    region: "us-east-1",
    asgName: autoScalingGroupWithLt.autoScalingGroupName,
    amiName: "ami-1"
  )
  void setupSpec() {
    validator = Stub(ModifyServerGroupLaunchTemplateValidator)
  }

  @Subject
  def prepareAction = new PrepareUpdateAutoScalingGroup(validator)

  @Unroll
  def "should prepare for update ASG as expected"() {
    given:
    description.launchTemplateOverridesForInstanceType = descOverrides

    def newDummyVersion = LaunchTemplateVersion.builder().launchTemplateId("lt-1").versionNumber(2L).build()
    def prepareCommand = new PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand.PrepareUpdateAutoScalingGroupCommandBuilder()
      .description(description)
      .launchTemplateVersion(newDummyVersion)
      .isReqToUpgradeAsgToMixedInstancesPolicy(false)
      .newLaunchTemplateVersionNumber(2L)
      .build()

    when:
    SagaAction.Result result = prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    result.nextCommand instanceof UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand
    ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).launchTemplateVersion == prepareCommand.launchTemplateVersion
    ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).isReqToUpgradeAsgToMixedInstancesPolicy == prepareCommand.isReqToUpgradeAsgToMixedInstancesPolicy
    ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).newLaunchTemplateVersionNumber == prepareCommand.newLaunchTemplateVersionNumber

    if (descOverrides) {
      ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).launchTemplateOverrides == [LaunchTemplateOverrides.builder().weightedCapacity("2").instanceType("m5.xlarge").build()]
    } else {
      ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).launchTemplateOverrides == null
    }

    where:
    descOverrides << [
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "m5.xlarge", weightedCapacity: 2)],
      null,
      []
    ]
  }
}
