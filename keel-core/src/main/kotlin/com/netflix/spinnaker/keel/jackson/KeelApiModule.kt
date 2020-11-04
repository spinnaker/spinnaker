package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module.SetupContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.Serializers
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.StaggeredRegion
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import com.netflix.spinnaker.keel.jackson.mixins.ClusterDeployStrategyMixin
import com.netflix.spinnaker.keel.jackson.mixins.CommitMixin
import com.netflix.spinnaker.keel.jackson.mixins.ConstraintStateMixin
import com.netflix.spinnaker.keel.jackson.mixins.DeliveryArtifactMixin
import com.netflix.spinnaker.keel.jackson.mixins.DeliveryConfigMixin
import com.netflix.spinnaker.keel.jackson.mixins.LocatableMixin
import com.netflix.spinnaker.keel.jackson.mixins.MonikeredMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceKindMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceSpecMixin
import com.netflix.spinnaker.keel.jackson.mixins.StaggeredRegionMixin
import com.netflix.spinnaker.keel.jackson.mixins.SubnetAwareRegionSpecMixin

fun ObjectMapper.registerKeelApiModule(): ObjectMapper = registerModule(KeelApiModule)

object KeelApiModule : SimpleModule("Keel API") {

  override fun setupModule(context: SetupContext) {
    with(context) {
      insertAnnotationIntrospector(KeelApiAnnotationIntrospector)
      addSerializers(KeelApiSerializers)
      addDeserializers(KeelApiDeserializers)
      setMixInAnnotations<ClusterDeployStrategy, ClusterDeployStrategyMixin>()
      setMixInAnnotations<ConstraintState, ConstraintStateMixin>()
      setMixInAnnotations<DeliveryArtifact, DeliveryArtifactMixin>()
      setMixInAnnotations<DeliveryConfig, DeliveryConfigMixin>()
      setMixInAnnotations<Locatable<*>, LocatableMixin<*>>()
      setMixInAnnotations<Monikered, MonikeredMixin>()
      setMixInAnnotations<ResourceKind, ResourceKindMixin>()
      setMixInAnnotations<StaggeredRegion, StaggeredRegionMixin>()
      setMixInAnnotations<SubnetAwareRegionSpec, SubnetAwareRegionSpecMixin>()
      setMixInAnnotations<Resource<*>, ResourceMixin>()
      setMixInAnnotations<ResourceSpec, ResourceSpecMixin>()
      setMixInAnnotations<Commit, CommitMixin>()
      insertAnnotationIntrospector(FactoryAnnotationIntrospector())
    }
  }
}

/**
 * Types in the `keel-api` module whose naming strategy is:
 *
 * ```
 * @JsonTypeInfo(
 *   use = Id.NAME,
 *   include = As.EXISTING_PROPERTY,
 *   property = "type"
 * )
 * ```
 *
 * can instead be added to this and not need to use the annotation.
 *
 * Sub-types need to be registered with Jackson (see how `ResourceSpec` implementations are
 * registered in [KeelConfigurationFinalizer] for example).
 */
internal object KeelApiAnnotationIntrospector : NopAnnotationIntrospector() {
  private val types = setOf(
    Constraint::class.java,
    ConstraintStateAttributes::class.java,
    DeliveryArtifact::class.java,
    VersioningStrategy::class.java
  )

  override fun findTypeResolver(config: MapperConfig<*>, ac: AnnotatedClass, baseType: JavaType): TypeResolverBuilder<*>? =
    if (baseType.rawClass in types) {
      StdTypeResolverBuilder()
        .init(Id.NAME, null)
        .inclusion(As.EXISTING_PROPERTY)
        .typeProperty("type")
    } else {
      null
    }
}

/**
 * Any custom [JsonSerializer] implementations for `keel-api` types.
 */
internal object KeelApiSerializers : Serializers.Base() {
  override fun findSerializer(config: SerializationConfig, type: JavaType, beanDesc: BeanDescription): JsonSerializer<*>? =
    when (type.rawClass) {
      TagVersionStrategy::class.java -> TagVersionStrategySerializer
      else -> null
    }
}

/**
 * Any custom [JsonDeserializer] implementations for `keel-api` types.
 */
internal object KeelApiDeserializers : Deserializers.Base() {
  override fun findEnumDeserializer(type: Class<*>, config: DeserializationConfig, beanDesc: BeanDescription): JsonDeserializer<*>? =
    when (type) {
      TagVersionStrategy::class.java -> TagVersionStrategyDeserializer
      else -> null
    }
}

internal inline fun <reified T> NamedType(name: String) = NamedType(T::class.java, name)

internal inline fun <reified TARGET, reified MIXIN> SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
