package com.netflix.spinnaker.keel.titus.jackson

import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.JavaType
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.deser.Deserializers
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling
import com.netflix.spinnaker.keel.titus.jackson.mixins.TitusClusterSpecMixin

fun JsonMapper.registerKeelTitusApiModule(): JsonMapper = rebuild()
  .addModule(KeelTitusApiModule)
  .build()

fun YAMLMapper.registerKeelTitusApiModule(): YAMLMapper = rebuild()
  .addModule(KeelTitusApiModule)
  .build()

object KeelTitusApiModule : SimpleModule("Keel Titus API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      addDeserializers(KeelTitusApiDeserializers)
      setMixIn<TitusClusterSpec, TitusClusterSpecMixin>()
    }
    super.setupModule(context)
  }
}

internal object KeelTitusApiDeserializers : Deserializers.Base() {
  override fun findBeanDeserializer(
    type: JavaType,
    config: DeserializationConfig,
    beanDesc: BeanDescription.Supplier
  ): ValueDeserializer<*>? =
    when (type.rawClass) {
      TitusScaling.Policy::class.java -> TitusScalingPolicyDescriptorDeserializer
      else -> null
    }

  override fun hasDeserializerFor(config: DeserializationConfig, valueType: Class<*>): Boolean =
    valueType == TitusScaling.Policy::class.java
}

private inline fun <reified TARGET, reified MIXIN> JacksonModule.SetupContext.setMixIn() {
  setMixIn(TARGET::class.java, MIXIN::class.java)
}
