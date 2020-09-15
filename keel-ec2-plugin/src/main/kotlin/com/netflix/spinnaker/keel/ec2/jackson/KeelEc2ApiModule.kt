package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonCreator.Mode.PROPERTIES
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.Serializers
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.IngressPorts
import com.netflix.spinnaker.keel.api.ec2.InstanceProvider
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.ActiveServerGroupImage
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.BuildInfo
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.Health
import com.netflix.spinnaker.keel.api.ec2.StepAdjustment
import com.netflix.spinnaker.keel.api.ec2.StepScalingPolicy
import com.netflix.spinnaker.keel.api.ec2.TargetGroupAttributes
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1Spec
import com.netflix.spinnaker.keel.api.schema.Factory
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ApplicationLoadBalancerSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ArtifactImageProviderMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.BuildInfoMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClassicLoadBalancerSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClusterDependenciesMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ClusterSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.CustomizedMetricSpecificationMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.HealthMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.HealthSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.InstanceProviderMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ScalingMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.SecurityGroupRuleMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.SecurityGroupSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.ServerGroupSpecMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.StepAdjustmentMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.StepScalingPolicyMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.TargetGroupAttributesMixin
import com.netflix.spinnaker.keel.ec2.jackson.mixins.TargetTrackingPolicyMixin
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.kotlinFunction

fun ObjectMapper.registerKeelEc2ApiModule(): ObjectMapper = registerModule(KeelEc2ApiModule)

object KeelEc2ApiModule : SimpleModule("Keel EC2 API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      addSerializers(KeelEc2ApiSerializers)
      addDeserializers(KeelEc2ApiDeserializers)

      setMixInAnnotations<ApplicationLoadBalancerSpec, ApplicationLoadBalancerSpecMixin>()
      // same annotations are required for this legacy model, so it can reuse the same mixin
      setMixInAnnotations<ApplicationLoadBalancerV1Spec, ApplicationLoadBalancerSpecMixin>()
      setMixInAnnotations<ArtifactImageProvider, ArtifactImageProviderMixin>()
      setMixInAnnotations<BuildInfo, BuildInfoMixin>()
      setMixInAnnotations<ClassicLoadBalancerSpec, ClassicLoadBalancerSpecMixin>()
      setMixInAnnotations<ClusterDependencies, ClusterDependenciesMixin>()
      setMixInAnnotations<ClusterSpec, ClusterSpecMixin>()
      setMixInAnnotations<CustomizedMetricSpecification, CustomizedMetricSpecificationMixin>()
      setMixInAnnotations<Health, HealthMixin>()
      setMixInAnnotations<HealthSpec, HealthSpecMixin>()
      setMixInAnnotations<InstanceProvider, InstanceProviderMixin>()
      setMixInAnnotations<Scaling, ScalingMixin>()
      setMixInAnnotations<SecurityGroupRule, SecurityGroupRuleMixin>()
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

internal object KeelEc2ApiSerializers : Serializers.Base() {
  override fun findSerializer(config: SerializationConfig, type: JavaType, beanDesc: BeanDescription): JsonSerializer<*>? =
    when {
      IngressPorts::class.java.isAssignableFrom(type.rawClass) -> IngressPortsSerializer()
      else -> null
    }
}

internal object KeelEc2ApiDeserializers : Deserializers.Base() {
  override fun findBeanDeserializer(type: JavaType, config: DeserializationConfig, beanDesc: BeanDescription): JsonDeserializer<*>? =
    when (type.rawClass) {
      ActiveServerGroupImage::class.java -> ActiveServerGroupImageDeserializer()
      ImageProvider::class.java -> ImageProviderDeserializer()
      IngressPorts::class.java -> IngressPortsDeserializer()
      SecurityGroupRule::class.java -> SecurityGroupRuleDeserializer()
      else -> null
    }
}

private inline fun <reified TARGET, reified MIXIN> Module.SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
