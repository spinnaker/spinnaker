package com.netflix.spinnaker.keel.serialization

import tools.jackson.databind.DeserializationContext
internal inline fun <reified T> DeserializationContext.instantiationException(cause: Throwable) =
  instantiationException(T::class.java, cause)
