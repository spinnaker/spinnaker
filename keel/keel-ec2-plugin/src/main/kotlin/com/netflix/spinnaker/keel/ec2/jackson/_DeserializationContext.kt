package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyMetadata
import com.fasterxml.jackson.databind.PropertyName

internal inline fun <reified T> DeserializationContext.findInjectableValue(
  valueId: String,
  propertyName: String = valueId
) =
  (parser.codec as ObjectMapper).let { mapper ->
    mapper.injectableValues.findInjectableValue(
      valueId,
      this,
      BeanProperty.Std(
        PropertyName.construct(propertyName),
        constructType(T::class.java),
        PropertyName.construct(propertyName),
        null,
        PropertyMetadata.STD_REQUIRED_OR_OPTIONAL
      ),
      null
    ) as T
  }
