package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.InstancesDistribution
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupResult
import com.amazonaws.services.ec2.model.*
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
  def autoScaling = Mock(AmazonAutoScaling)
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
  def ltVersion = new LaunchTemplateVersion(
    launchTemplateId: "lt-1",
    launchTemplateData: new ResponseLaunchTemplateData(
      imageId: "ami-1",
    ),
    versionNumber: 3L
  )

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
    def asgWithMip = new AutoScalingGroup(
      autoScalingGroupName: asgName,
      mixedInstancesPolicy: new MixedInstancesPolicy() // description is already populated with MIP values from existing ASG at this point, use a dummy MIP here
    )

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.getVersionNumber())
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

    assert updateReq.mixedInstancesPolicy.instancesDistribution == new InstancesDistribution(
      onDemandAllocationStrategy: "prioritized",
      onDemandBaseCapacity: 2,
      onDemandPercentageAboveBaseCapacity: 50,
      spotAllocationStrategy: "capacity-optimized",
      spotInstancePools: null,
      spotMaxPrice: "1"
    )
    assert updateReq.mixedInstancesPolicy.launchTemplate.launchTemplateSpecification == new LaunchTemplateSpecification(
      launchTemplateId: ltVersion.launchTemplateId,
      version: String.valueOf(ltVersion.getVersionNumber())
    )
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
    def asgWithLt = new AutoScalingGroup(
      autoScalingGroupName: asgName,
      launchTemplate: new LaunchTemplateSpecification(launchTemplateName: ltVersion.launchTemplateId, version: String.valueOf(ltVersion.getVersionNumber()))
    )

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.getVersionNumber())
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
      assert updateReq.launchTemplate.version == String.valueOf(ltVersion.getVersionNumber()); new UpdateAutoScalingGroupResult()
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
    def asgWithLt = new AutoScalingGroup(
      autoScalingGroupName: asgName,
      launchTemplate: new LaunchTemplateSpecification(launchTemplateId: ltVersion.launchTemplateId, version: String.valueOf(ltVersion.getVersionNumber()))
    )

    and:
    def updateCommand = new UpdateAutoScalingGroup.UpdateAutoScalingGroupCommand.UpdateAutoScalingGroupCommandBuilder()
      .description(modifyDesc)
      .launchTemplateVersion(ltVersion)
      .newLaunchTemplateVersionNumber(ltVersion.getVersionNumber())
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
      assert updateReq.mixedInstancesPolicy.instancesDistribution == new InstancesDistribution(
        onDemandAllocationStrategy: null,
        onDemandBaseCapacity: null,
        onDemandPercentageAboveBaseCapacity: null,
        spotAllocationStrategy: "capacity-optimized",
        spotInstancePools: null,
        spotMaxPrice: "1"
      )
      assert updateReq.mixedInstancesPolicy.launchTemplate.launchTemplateSpecification == new LaunchTemplateSpecification(
        launchTemplateId: ltVersion.launchTemplateId,
        version: String.valueOf(ltVersion.getVersionNumber())
      )

      assert updateReq.mixedInstancesPolicy.launchTemplate.overrides == []; new UpdateAutoScalingGroupResult()
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
    def asgWithLt = new AutoScalingGroup(
      autoScalingGroupName: asgName,
      launchTemplate: new LaunchTemplateSpecification(launchTemplateName: ltVersion.launchTemplateId, version: String.valueOf(ltVersion.getVersionNumber()))
    )

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
