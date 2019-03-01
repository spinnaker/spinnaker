package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_WITH_ZONE_ID
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.huxhorn.sulky.ulid.ULID
import java.text.SimpleDateFormat
import java.util.*

/**
 * Factory method for [ObjectMapper]s configured how we like 'em.
 */
fun configuredObjectMapper(): ObjectMapper =
  ObjectMapper().configureMe()

/**
 * Factory method for [YAMLMapper]s configured how we like 'em.
 */
fun configuredYamlMapper(): ObjectMapper =
  YAMLMapper().configureMe()

private fun ObjectMapper.configureMe() =
  registerKotlinModule()
    .registerULIDModule()
    .registerModule(JavaTimeModule())
    .configureSaneDateTimeRepresentation()
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)

private fun ObjectMapper.registerULIDModule() =
  registerModule(SimpleModule().apply {
    addSerializer(ULID.Value::class.java, ToStringSerializer())
    addDeserializer(ULID.Value::class.java, ULIDDeserializer())
  })

private fun ObjectMapper.configureSaneDateTimeRepresentation() =
  enable(WRITE_DATES_AS_TIMESTAMPS)
    .enable(WRITE_DATES_WITH_ZONE_ID)
    .enable(WRITE_DATE_KEYS_AS_TIMESTAMPS)
    .apply {
      dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").apply {
        timeZone = TimeZone.getDefault()
      }
    }
