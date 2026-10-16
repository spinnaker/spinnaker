package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.databind.JacksonModule
import tools.jackson.databind.jsontype.NamedType
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.InstanceProvider
import com.netflix.spinnaker.keel.api.ec2.PrefixListRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.BuildInfo
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.Health
import com.netflix.spinnaker.keel.api.ec2.StepAdjustment
import com.netflix.spinnaker.keel.api.ec2.StepScalingPolicy
import com.netflix.spinnaker.keel.api.ec2.TargetGroupAttributes
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ActionMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ApplicationLoadBalancerSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.BuildInfoMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClassicLoadBalancerSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClusterDependenciesMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClusterSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClusterV1SpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.CustomizedMetricSpecificationMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.HealthMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.HealthSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.InstanceProviderMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ReferenceRuleMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ScalingMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.SecurityGroupSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ServerGroupSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.StepAdjustmentMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.StepScalingPolicyMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.TargetGroupAttributesMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.TargetTrackingPolicyMixin

val SECURITY_GROUP_RULE_SUBTYPES = mapOf(
  ReferenceRule::class.java to "reference",
  CrossAccountReferenceRule::class.java to "cross-account",
  CidrRule::class.java to "cidr",
  PrefixListRule::class.java to "prefix-list"
)

fun JsonMapper.registerKeelEc2ApiModule(): JsonMapper = rebuild()
  .addModule(KeelEc2ApiModule)
  .registerSubtypes(*SECURITY_GROUP_RULE_SUBTYPES.map { (subType, discriminator) -> NamedType(subType, discriminator) }.toTypedArray())
  .build()

fun YAMLMapper.registerKeelEc2ApiModule(): YAMLMapper = rebuild()
  .addModule(KeelEc2ApiModule)
  .registerSubtypes(*SECURITY_GROUP_RULE_SUBTYPES.map { (subType, discriminator) -> NamedType(subType, discriminator) }.toTypedArray())
  .build()

fun ExtensionRegistry.registerEc2Subtypes() {
  // Note that the discriminators below are not used as sub-types are determined by the custom deserializer above
  SECURITY_GROUP_RULE_SUBTYPES.forEach { (subType, discriminator) ->
    register(SecurityGroupRule::class.java, subType, discriminator)
  }
}

internal object KeelEc2ApiModule : SimpleModule("Keel EC2 API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      setMixIn<ApplicationLoadBalancerSpec.Action, ActionMixin>()
      setMixIn<ApplicationLoadBalancerSpec, ApplicationLoadBalancerSpecMixin>()
      // same annotations are required for these legacy models, so they can reuse the same mixin
      setMixIn<ApplicationLoadBalancerV1_1Spec, ApplicationLoadBalancerSpecMixin>()
      setMixIn<ApplicationLoadBalancerV1Spec, ApplicationLoadBalancerSpecMixin>()
      setMixIn<BuildInfo, BuildInfoMixin>()
      setMixIn<ClassicLoadBalancerSpec, ClassicLoadBalancerSpecMixin>()
      setMixIn<ClusterDependencies, ClusterDependenciesMixin>()
      setMixIn<ClusterSpec, ClusterSpecMixin>()
      setMixIn<ClusterV1Spec, ClusterV1SpecMixin>()
      setMixIn<CustomizedMetricSpecification, CustomizedMetricSpecificationMixin>()
      setMixIn<Health, HealthMixin>()
      setMixIn<HealthSpec, HealthSpecMixin>()
      setMixIn<InstanceProvider, InstanceProviderMixin>()
      setMixIn<ReferenceRule, ReferenceRuleMixin>()
      setMixIn<Scaling, ScalingMixin>()
      setMixIn<SecurityGroupSpec, SecurityGroupSpecMixin>()
      setMixIn<ServerGroupSpec, ServerGroupSpecMixin>()
      setMixIn<StepAdjustment, StepAdjustmentMixin>()
      setMixIn<StepScalingPolicy, StepScalingPolicyMixin>()
      setMixIn<TargetGroupAttributes, TargetGroupAttributesMixin>()
      setMixIn<TargetTrackingPolicy, TargetTrackingPolicyMixin>()
    }
    super.setupModule(context)
  }
}

private inline fun <reified TARGET, reified MIXIN> JacksonModule.SetupContext.setMixIn() {
  setMixIn(TARGET::class.java, MIXIN::class.java)
}
