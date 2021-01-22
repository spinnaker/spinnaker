package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.module.SimpleModule
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
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
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
  CidrRule::class.java to "cidr"
)

fun ObjectMapper.registerKeelEc2ApiModule(): ObjectMapper {
  return registerModule(KeelEc2ApiModule)
    .apply {
      SECURITY_GROUP_RULE_SUBTYPES.forEach { (subType, discriminator) ->
        registerSubtypes(NamedType(subType, discriminator))
      }
    }
}

fun ExtensionRegistry.registerEc2Subtypes() {
  // Note that the discriminators below are not used as sub-types are determined by the custom deserializer above
  SECURITY_GROUP_RULE_SUBTYPES.forEach { (subType, discriminator) ->
    register(SecurityGroupRule::class.java, subType, discriminator)
  }
}

internal object KeelEc2ApiModule : SimpleModule("Keel EC2 API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      setMixInAnnotations<ApplicationLoadBalancerSpec, ApplicationLoadBalancerSpecMixin>()
      // same annotations are required for this legacy model, so it can reuse the same mixin
      setMixInAnnotations<ApplicationLoadBalancerV1Spec, ApplicationLoadBalancerSpecMixin>()
      setMixInAnnotations<BuildInfo, BuildInfoMixin>()
      setMixInAnnotations<ClassicLoadBalancerSpec, ClassicLoadBalancerSpecMixin>()
      setMixInAnnotations<ClusterDependencies, ClusterDependenciesMixin>()
      setMixInAnnotations<ClusterSpec, ClusterSpecMixin>()
      setMixInAnnotations<ClusterV1Spec, ClusterV1SpecMixin>()
      setMixInAnnotations<CustomizedMetricSpecification, CustomizedMetricSpecificationMixin>()
      setMixInAnnotations<Health, HealthMixin>()
      setMixInAnnotations<HealthSpec, HealthSpecMixin>()
      setMixInAnnotations<InstanceProvider, InstanceProviderMixin>()
      setMixInAnnotations<ReferenceRule, ReferenceRuleMixin>()
      setMixInAnnotations<Scaling, ScalingMixin>()
      setMixInAnnotations<SecurityGroupSpec, SecurityGroupSpecMixin>()
      setMixInAnnotations<ServerGroupSpec, ServerGroupSpecMixin>()
      setMixInAnnotations<StepAdjustment, StepAdjustmentMixin>()
      setMixInAnnotations<StepScalingPolicy, StepScalingPolicyMixin>()
      setMixInAnnotations<TargetGroupAttributes, TargetGroupAttributesMixin>()
      setMixInAnnotations<TargetTrackingPolicy, TargetTrackingPolicyMixin>()
    }
    super.setupModule(context)
  }
}

private inline fun <reified TARGET, reified MIXIN> Module.SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
