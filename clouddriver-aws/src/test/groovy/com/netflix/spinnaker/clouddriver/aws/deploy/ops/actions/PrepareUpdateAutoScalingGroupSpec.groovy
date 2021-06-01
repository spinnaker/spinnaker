package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PrepareUpdateAutoScalingGroupSpec extends Specification {

  def autoScalingGroupWithLt = new AutoScalingGroup(
    autoScalingGroupName: "test-v001",
    launchTemplate: new LaunchTemplateSpecification(launchTemplateName: "lt-1", version: "1")
  )

  def description = new ModifyServerGroupLaunchTemplateDescription(
    region: "us-east-1",
    asgName: autoScalingGroupWithLt.autoScalingGroupName,
    amiName: "ami-1"
  )

  @Subject
  def prepareAction = new PrepareUpdateAutoScalingGroup()

  @Unroll
  def "should prepare for update ASG as expected"() {
    given:
    description.launchTemplateOverridesForInstanceType = descOverrides

    def newDummyVersion = new LaunchTemplateVersion(launchTemplateId: "lt-1", versionNumber: 2L)
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
      ((UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand) result.nextCommand).launchTemplateOverrides == [new LaunchTemplateOverrides().withWeightedCapacity(2).withInstanceType("m5.xlarge")]
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
