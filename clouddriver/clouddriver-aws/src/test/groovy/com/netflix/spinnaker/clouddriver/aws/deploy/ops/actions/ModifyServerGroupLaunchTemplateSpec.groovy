package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.ec2.model.*
import com.fasterxml.jackson.databind.JsonMappingException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ModifyServerGroupLaunchTemplateSpec extends Specification {
  def credentials = TestCredential.named("test")
  def ltService = Mock(LaunchTemplateService)
  def credentialsRepository = Stub(MapBackedCredentialsRepository) {
    getOne(_) >> credentials
  }

  def autoScalingGroupWithLt = new AutoScalingGroup(
    autoScalingGroupName: "test-v001",
    launchTemplate: new LaunchTemplateSpecification(launchTemplateName: "lt-1", version: "1")
  )

  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getLaunchTemplateService() >> ltService
  }

  def regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
    forRegion(_, _) >> regionScopedProvider
  }

  def dummyDescription = new ModifyServerGroupLaunchTemplateDescription(
    region: "us-east-1",
    asgName: autoScalingGroupWithLt.autoScalingGroupName,
    amiName: "ami-1",
    credentials: credentials
  )

  @Subject
  def modifyAction = new ModifyServerGroupLaunchTemplate(credentialsRepository, regionScopedProviderFactory)

  @Unroll
  def "should modify launch template as expected"() {
    given:
    def dummySrcVersion = new LaunchTemplateVersion()
    def modifyCommand = new ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand.ModifyServerGroupLaunchTemplateCommandBuilder()
      .description(dummyDescription)
      .isAsgBackedByMixedInstancesPolicy(asgBackedByMip)
      .isReqToModifyLaunchTemplate(reqToModifyLaunchTemplate)
      .isReqToUpgradeAsgToMixedInstancesPolicy(convertAsgToUseMip)
      .sourceVersion(dummySrcVersion)
      .build()

    def newDummyVersion = new LaunchTemplateVersion(launchTemplateId: "lt-1", versionNumber: 2L)

    when:
    SagaAction.Result result = modifyAction.apply(modifyCommand, new Saga("test", "test"))

    then:
    result.nextCommand instanceof PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand
    ((PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand) result.nextCommand).newLaunchTemplateVersionNumber == newLtVersionNumber
    ((PrepareUpdateAutoScalingGroup.PrepareUpdateAutoScalingGroupCommand) result.nextCommand).isReqToUpgradeAsgToMixedInstancesPolicy == convertAsgToUseMip
    if (reqToModifyLaunchTemplate) {
      1 * ltService.modifyLaunchTemplate(credentials, dummyDescription, dummySrcVersion, shouldUseMipInModify) >> newDummyVersion
    } else {
      // modify action skipped
      0 * ltService.modifyLaunchTemplate(credentials, dummyDescription, dummySrcVersion, shouldUseMipInModify) >> newDummyVersion
    }

    where:
    asgBackedByMip    | reqToModifyLaunchTemplate | convertAsgToUseMip  || shouldUseMipInModify || newLtVersionNumber
      true            |        true               |        false        ||      true            ||      2L            // update ASG MIP with new LT version
      true            |        false              |        false        ||      true            ||     null           // update ASG MIP properties without creating a new LT version
      false           |        true               |        true         ||      true            ||      2L            // update ASG LT with new LT version and convert ASG to use MIP
      false           |        true               |        false        ||      false           ||      2L            // update ASG LT with new LT version, but don't use MIP
  }

  def "should not throw JsonProcessingException when deserializing"() {
    given:
    def objectMapper = AmazonObjectMapperConfigurer.createConfigured()
    def json = objectMapper.writeValueAsString(dummyDescription)

    when:
    objectMapper.readValue(json, ModifyServerGroupLaunchTemplateDescription.class)

    then:
    notThrown(JsonMappingException)
  }
}
