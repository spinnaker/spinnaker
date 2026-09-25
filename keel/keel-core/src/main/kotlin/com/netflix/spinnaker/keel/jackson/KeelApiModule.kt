package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.annotation.JsonFormat
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.JavaType
import tools.jackson.databind.JacksonModule.SetupContext
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.cfg.MapperConfig
import tools.jackson.databind.deser.Deserializers
import tools.jackson.databind.introspect.AnnotatedClass
import tools.jackson.databind.introspect.Annotated
import tools.jackson.databind.introspect.NopAnnotationIntrospector
import tools.jackson.databind.jsontype.NamedType
import tools.jackson.databind.jsontype.TypeResolverBuilder
import tools.jackson.databind.jsontype.impl.StdTypeResolverBuilder
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.Serializers
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.StaggeredRegion
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.jackson.mixins.ClusterDeployStrategyMixin
import com.netflix.spinnaker.keel.jackson.mixins.CommitMixin
import com.netflix.spinnaker.keel.jackson.mixins.ConstraintStateMixin
import com.netflix.spinnaker.keel.jackson.mixins.DeliveryArtifactMixin
import com.netflix.spinnaker.keel.jackson.mixins.DeliveryConfigMixin
import com.netflix.spinnaker.keel.jackson.mixins.DependentMixin
import com.netflix.spinnaker.keel.jackson.mixins.LocatableMixin
import com.netflix.spinnaker.keel.jackson.mixins.MonikeredMixin
import com.netflix.spinnaker.keel.jackson.mixins.PreviewEnvironmentSpecMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceKindMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceMixin
import com.netflix.spinnaker.keel.jackson.mixins.ResourceSpecMixin
import com.netflix.spinnaker.keel.jackson.mixins.StaggeredRegionMixin
import com.netflix.spinnaker.keel.jackson.mixins.SubnetAwareRegionSpecMixin
import com.netflix.spinnaker.keel.jackson.mixins.VerificationMixin

fun JsonMapper.registerKeelApiModule(): JsonMapper =
  rebuild().addModule(KeelApiModule).build()

fun YAMLMapper.registerKeelApiModule(): YAMLMapper =
  rebuild().addModule(KeelApiModule).build()

object KeelApiModule : SimpleModule("Keel API") {

  override fun setupModule(context: SetupContext) {
    with(context) {
      insertAnnotationIntrospector(KeelApiAnnotationIntrospector)
      addSerializers(KeelApiSerializers)
      addDeserializers(KeelApiDeserializers)
      setMixIn<ClusterDeployStrategy, ClusterDeployStrategyMixin>()
      setMixIn<ConstraintState, ConstraintStateMixin>()
      setMixIn<DeliveryArtifact, DeliveryArtifactMixin>()
      setMixIn<DeliveryConfig, DeliveryConfigMixin>()
      setMixIn<Locatable<*>, LocatableMixin<*>>()
      setMixIn<Monikered, MonikeredMixin>()
      setMixIn<ResourceKind, ResourceKindMixin>()
      setMixIn<StaggeredRegion, StaggeredRegionMixin>()
      setMixIn<SubnetAwareRegionSpec, SubnetAwareRegionSpecMixin>()
      setMixIn<Resource<*>, ResourceMixin>()
      setMixIn<ResourceSpec, ResourceSpecMixin>()
      setMixIn<Commit, CommitMixin>()
      setMixIn<Verification, VerificationMixin>()
      setMixIn<PreviewEnvironmentSpec, PreviewEnvironmentSpecMixin>()
      setMixIn<Dependent, DependentMixin>()
      insertAnnotationIntrospector(FactoryAnnotationIntrospector())
    }
    super.setupModule(context)
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
    SortingStrategy::class.java,
    Verification::class.java,
    PostDeployAction::class.java
  )

  override fun findTypeResolverBuilder(config: MapperConfig<*>, ac: Annotated): Any? =
    if (ac.rawType in types) {
      StdTypeResolverBuilder(Id.NAME, As.EXISTING_PROPERTY, "type")
    } else {
      null
    }
}

/**
 * Any custom [JsonSerializer] implementations for `keel-api` types.
 */
internal object KeelApiSerializers : Serializers.Base() {
  override fun findSerializer(
    config: SerializationConfig,
    type: JavaType,
    beanDesc: BeanDescription.Supplier,
    format: JsonFormat.Value?
  ): ValueSerializer<*>? =
    when (type.rawClass) {
      TagVersionStrategy::class.java -> TagVersionStrategySerializer
      else -> null
    }
}

/**
 * Any custom [JsonDeserializer] implementations for `keel-api` types.
 */
internal object KeelApiDeserializers : Deserializers.Base() {
  override fun findEnumDeserializer(
    type: JavaType,
    config: DeserializationConfig,
    beanDesc: BeanDescription.Supplier
  ): ValueDeserializer<*>? =
    when (type.rawClass) {
      TagVersionStrategy::class.java -> TagVersionStrategyDeserializer
      else -> null
    }

  override fun hasDeserializerFor(config: DeserializationConfig, valueType: Class<*>): Boolean =
    valueType == TagVersionStrategy::class.java
}

internal inline fun <reified T> NamedType(name: String) = NamedType(T::class.java, name)

internal inline fun <reified TARGET, reified MIXIN> SetupContext.setMixIn() {
  setMixIn(TARGET::class.java, MIXIN::class.java)
}
