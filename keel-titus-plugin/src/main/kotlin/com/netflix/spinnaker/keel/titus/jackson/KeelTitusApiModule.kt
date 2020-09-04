package com.netflix.spinnaker.keel.titus.jackson

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.titus.jackson.mixins.TitusClusterSpecMixin

fun ObjectMapper.registerKeelTitusApiModule(): ObjectMapper = registerModule(KeelTitusApiModule)

object KeelTitusApiModule : SimpleModule("Keel Titus API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      addDeserializers(KeelTitusApiDeserializers)

      setMixInAnnotations<TitusClusterSpec, TitusClusterSpecMixin>()
    }
    super.setupModule(context)
  }
}

internal object KeelTitusApiDeserializers : Deserializers.Base() {
  override fun findBeanDeserializer(type: JavaType, config: DeserializationConfig, beanDesc: BeanDescription): JsonDeserializer<*>? =
    when (type.rawClass) {
      TitusClusterSpec::class.java -> TitusClusterSpecDeserializer()
      else -> null
    }
}

private inline fun <reified TARGET, reified MIXIN> Module.SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
