package com.netflix.spinnaker.keel

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService

inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T): T =
  getConfig(T::class.java, configName, defaultValue)
