package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.annotation.JsonTypeInfo
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
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DebianSemVerVersioningStrategy
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.DockerVersioningStrategy
import com.netflix.spinnaker.keel.api.TagVersionStrategy
import com.netflix.spinnaker.keel.api.VersioningStrategy

fun ObjectMapper.registerKeelApiModule(): ObjectMapper = registerModule(KeelApiModule)

object KeelApiModule : SimpleModule("Keel API") {

  override fun setupModule(context: SetupContext) {
    with(context) {
      insertAnnotationIntrospector(KeelApiAnnotationIntrospector)
      addSerializers(KeelApiSerializers)
      addDeserializers(KeelApiDeserializers)
      setMixInAnnotations<DeliveryArtifact, DeliveryArtifactMixin>()

      registerSubtypes(
        NamedType<DebianArtifact>(ArtifactType.deb.name),
        NamedType<DockerArtifact>(ArtifactType.docker.name),
        NamedType<DockerVersioningStrategy>(ArtifactType.docker.name),
        NamedType<DebianSemVerVersioningStrategy>(ArtifactType.deb.name)
      )
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
 * registered, for example).
 */
internal object KeelApiAnnotationIntrospector : NopAnnotationIntrospector() {
  private val types = setOf(
    Constraint::class.java,
    DeliveryArtifact::class.java
  )

  override fun findTypeResolver(config: MapperConfig<*>, ac: AnnotatedClass, baseType: JavaType): TypeResolverBuilder<*>? =
    if (baseType.rawClass in types) {
      StdTypeResolverBuilder()
        .init(JsonTypeInfo.Id.NAME, null)
        .inclusion(JsonTypeInfo.As.EXISTING_PROPERTY)
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

  override fun findBeanDeserializer(type: JavaType, config: DeserializationConfig, beanDesc: BeanDescription): JsonDeserializer<*>? =
    when (type.rawClass) {
      VersioningStrategy::class.java -> VersioningStrategyDeserializer
      else -> null
    }
}

internal inline fun <reified T> NamedType(name: String) = NamedType(T::class.java, name)

internal inline fun <reified TARGET, reified MIXIN> SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
