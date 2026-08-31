package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupResponse
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ModifyServerGroupLaunchTemplateAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpdateAutoScalingGroupSpec extends Specification {
  def credentials = TestCredential.named("test")
  def autoScaling = Mock(AutoScalingClient)
  def ltService = Mock(LaunchTemplateService)
  def asgService = Mock(AsgService)
  def credentialsRepository = Stub(MapBackedCredentialsRepository) {
    getOne(_) >> credentials
  }

  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getAsgService() >> asgService
    getAutoScaling() >> autoScaling
    getLaunchTemplateService() >> ltService
  }

  def regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
    forRegion(_, _) >> regionScopedProvider
  }

  @Subject
  def updateAction = new UpdateAutoScalingGroup(regionScopedProviderFactory, credentialsRepository)

  def asgName = "test-v001"
  def ltVersion = LaunchTemplateVersion.builder()
    .launchTemplateId("lt-1")
    .launchTemplateData(ResponseLaunchTemplateData.builder().imageId("ami-1").build())
    .versionNumber(3L)
    .build()

  def "should update ASG backed by mixed instances policy correctly"() {
    given:
    def modifyDesc = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: asgName,
      amiName: "ami-1",
      credentials: credentials,
      spotAllocationStrategy: "capacity-optimized",
      onDemandAllocationStrategy: "prioritized",
      onDemandBaseCapacity: 2,
      onDemandPercentageAboveBaseCapacity: 50,
      spotPrice: "1"
    )

    and:
    def asgWithMip = AutoScalingGroup.builder()
      .autoScalingGroupName(asgName)
      .mixedInstancesPolicy(MixedInstancesPolicy.builder().build()) // description is already populated with MIP values from existing ASG at this point, use a dummy MIP here
      .build()

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.versionNumber())
      .launchTemplateOverrides(null)
      .isReqToUpgradeAsgToMixedInstancesPolicy(false)
      .build()

    when:
    updateAction.apply(updateCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> asgWithMip
    1 * autoScaling.updateAutoScalingGroup(_ as UpdateAutoScalingGroupRequest) >> { arguments ->
      // assert arguments passed and return
      UpdateAutoScalingGroupRequest updateReq = arguments[0]

    assert updateReq.autoScalingGroupName == asgName
    assert updateReq.launchTemplate == null

    assert updateReq.mixedInstancesPolicy.instancesDistribution == InstancesDistribution.builder()
      .onDemandAllocationStrategy("prioritized")
      .onDemandBaseCapacity(2)
      .onDemandPercentageAboveBaseCapacity(50)
      .spotAllocationStrategy("capacity-optimized")
      .spotInstancePools(null)
      .spotMaxPrice("1")
      .build()
    assert updateReq.mixedInstancesPolicy.launchTemplate.launchTemplateSpecification == LaunchTemplateSpecification.builder()
      .launchTemplateId(ltVersion.launchTemplateId)
      .version(String.valueOf(ltVersion.versionNumber()))
      .build()
    }
  }

  def "should update ASG backed by launch template correctly"() {
    given:
    def modifyDesc = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: asgName,
      amiName: "ami-1",
      credentials: credentials,
      instanceType: "new.type"
    )

    and:
    def asgWithLt = AutoScalingGroup.builder()
      .autoScalingGroupName(asgName)
      .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateName(ltVersion.launchTemplateId).version(String.valueOf(ltVersion.versionNumber())).build())
      .build()

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.versionNumber())
      .launchTemplateOverrides(null)
      .isReqToUpgradeAsgToMixedInstancesPolicy(false)
      .build()

    when:
    updateAction.apply(updateCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> asgWithLt
    1 * autoScaling.updateAutoScalingGroup(_ as UpdateAutoScalingGroupRequest) >> { arguments ->
      // assert arguments passed and return
      UpdateAutoScalingGroupRequest updateReq = arguments[0]

      assert updateReq.autoScalingGroupName == asgName
      assert updateReq.mixedInstancesPolicy == null

      assert updateReq.launchTemplate.launchTemplateId == ltVersion.launchTemplateId
      assert updateReq.launchTemplate.version == String.valueOf(ltVersion.versionNumber()); UpdateAutoScalingGroupResponse.builder().build()
    }
  }

  def "should convert ASG backed by launch template to use mixed instances policy correctly"() {
    given:
    def modifyDesc = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: asgName,
      amiName: "ami-1",
      credentials: credentials,
      spotAllocationStrategy: "capacity-optimized",
      spotPrice: "1"
    )

    and:
    def asgWithLt = AutoScalingGroup.builder()
      .autoScalingGroupName(asgName)
      .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateId(ltVersion.launchTemplateId).version(String.valueOf(ltVersion.versionNumber())).build())
      .build()

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.versionNumber())
      .launchTemplateOverrides(null)
      .isReqToUpgradeAsgToMixedInstancesPolicy(true)
      .build()

    when:
    updateAction.apply(updateCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> asgWithLt
    1 * autoScaling.updateAutoScalingGroup(_ as UpdateAutoScalingGroupRequest) >> { arguments ->
      // assert arguments passed and return
      UpdateAutoScalingGroupRequest updateReq = arguments[0]

      assert updateReq.autoScalingGroupName == asgName
      assert updateReq.launchTemplate == null

      // null values will take AWS defaults
      assert updateReq.mixedInstancesPolicy.instancesDistribution == InstancesDistribution.builder()
        .onDemandAllocationStrategy(null)
        .onDemandBaseCapacity(null)
        .onDemandPercentageAboveBaseCapacity(null)
        .spotAllocationStrategy("capacity-optimized")
        .spotInstancePools(null)
        .spotMaxPrice("1")
        .build()
      assert updateReq.mixedInstancesPolicy.launchTemplate.launchTemplateSpecification == LaunchTemplateSpecification.builder()
        .launchTemplateId(ltVersion.launchTemplateId)
        .version(String.valueOf(ltVersion.versionNumber()))
        .build()

      assert updateReq.mixedInstancesPolicy.launchTemplate.overrides == []; UpdateAutoScalingGroupResponse.builder().build()
    }
  }

  @Unroll
  def "should clean up newly created launch template version on failure"() {
    given:
    def modifyDesc = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: asgName,
      amiName: "ami-1",
      credentials: credentials,
      instanceType: "new.type"
    )

    and:
    def asgWithLt = AutoScalingGroup.builder()
      .autoScalingGroupName(asgName)
      .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateName(ltVersion.launchTemplateId).version(String.valueOf(ltVersion.versionNumber())).build())
      .build()

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(newLtVersionNum)
      .launchTemplateOverrides(null)
      .isReqToUpgradeAsgToMixedInstancesPolicy(false)
      .build()

    when:
    updateAction.apply(updateCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> asgWithLt
    1 * autoScaling.updateAutoScalingGroup(_) >> { throw new RuntimeException("Update ASG failed!")}
    Exception ex = thrown(ModifyServerGroupLaunchTemplateAtomicOperation.LaunchTemplateException.class)

    // verify clean up and exception message
    if (newLtVersionNum) {
      if (deleteLtVersionFailed) {
        1 * ltService.deleteLaunchTemplateVersion(ltVersion.launchTemplateId, newLtVersionNum) >> { throw new RuntimeException("Failed to delete launch template version $newLtVersionNum for launch template ID $ltVersion.launchTemplateId because of error 'unexpectedError'") }
      } else {
        1 * ltService.deleteLaunchTemplateVersion(ltVersion.launchTemplateId, newLtVersionNum)
      }
    }
    ex.message == exceptionMsgExpected

    where:
    newLtVersionNum | deleteLtVersionFailed |  exceptionMsgExpected
          null      |          _            | "Failed to update server group test-v001.Error: Update ASG failed!\n"
          3L        |         true          | "Failed to update server group test-v001.Error: Update ASG failed!\nFailed to clean up launch template version! Error: Failed to delete launch template version 3 for launch template ID lt-1 because of error 'unexpectedError'"
          3L        |         false         | "Failed to update server group test-v001.Error: Update ASG failed!\n"
  }
}
