package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.databind.DeserializationContext

internal inline fun <reified T> DeserializationContext.findInjectableValue(
  valueId: String,
  propertyName: String = valueId
): T = (getAttribute(valueId) ?: error("No injectable value for '$valueId'")) as T
