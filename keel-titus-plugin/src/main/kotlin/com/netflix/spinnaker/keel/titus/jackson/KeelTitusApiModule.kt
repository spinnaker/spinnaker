package com.netflix.spinnaker.keel.titus.jackson

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.titus.jackson.mixins.TitusClusterSpecMixin

fun ObjectMapper.registerKeelTitusApiModule(): ObjectMapper = registerModule(KeelTitusApiModule)

object KeelTitusApiModule : SimpleModule("Keel Titus API") {
  override fun setupModule(context: SetupContext) {
    with(context) {
      setMixInAnnotations<TitusClusterSpec, TitusClusterSpecMixin>()
    }
    super.setupModule(context)
  }
}

private inline fun <reified TARGET, reified MIXIN> Module.SetupContext.setMixInAnnotations() {
  setMixInAnnotations(TARGET::class.java, MIXIN::class.java)
}
