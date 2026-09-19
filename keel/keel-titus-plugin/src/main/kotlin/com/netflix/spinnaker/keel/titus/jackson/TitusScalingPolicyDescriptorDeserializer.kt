package com.netflix.spinnaker.keel.titus.jackson

import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling
import com.netflix.spinnaker.keel.jackson.PropertyNamePolymorphicDeserializer

object TitusScalingPolicyDescriptorDeserializer :
  PropertyNamePolymorphicDeserializer<TitusScaling.Policy>(TitusScaling.Policy::class.java) {
  override fun identifySubType(
    root: JsonNode,
    context: DeserializationContext,
    fieldNames: Collection<String>
  ): Class<out TitusScaling.Policy> =
    when {
      "targetPolicyDescriptor" in fieldNames -> TitusScaling.Policy.TargetPolicy::class.java
      "stepPolicyDescriptor" in fieldNames -> TitusScaling.Policy.StepPolicy::class.java
      else -> super.identifySubType(root, context, fieldNames)
    }
}
