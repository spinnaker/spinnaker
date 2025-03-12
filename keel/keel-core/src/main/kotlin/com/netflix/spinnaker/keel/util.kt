package com.netflix.spinnaker.keel

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.jackson.readValueInliningAliases
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService

inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T): T =
  getConfig(T::class.java, configName, defaultValue)

fun YAMLMapper.parseDeliveryConfig(rawDeliveryConfig: String): SubmittedDeliveryConfig {
  return readValueInliningAliases<SubmittedDeliveryConfig>(rawDeliveryConfig)
    .copy(rawConfig = rawDeliveryConfig)
}
