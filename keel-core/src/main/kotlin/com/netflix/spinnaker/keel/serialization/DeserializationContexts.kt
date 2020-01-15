package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper

internal val DeserializationContext.mapper: ObjectMapper
  // Such an intuitive API. ObjectMapper is the only thing that implements ObjectCodec but useful
  // methods like convertValue are not on that interface so we need to do a dirty type cast.
  get() = parser.codec as ObjectMapper

internal inline fun <reified T> DeserializationContext.instantiationException(cause: Throwable) =
  instantiationException(T::class.java, cause)
