package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_WITH_ZONE_ID
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.USE_NATIVE_TYPE_ID
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.json.registerKeelApiModule
import de.huxhorn.sulky.ulid.ULID
import java.text.SimpleDateFormat
import java.util.TimeZone

/**
 * Factory method for [ObjectMapper]s configured how we like 'em.
 */
fun configuredObjectMapper(): ObjectMapper =
  ObjectMapper().configureMe()

/**
 * Factory method for [YAMLMapper]s configured how we like 'em.
 */
fun configuredYamlMapper(): YAMLMapper =
  YAMLMapper()
    .configureMe()
    .disable(USE_NATIVE_TYPE_ID)

private fun <T : ObjectMapper> T.configureMe(): T =
  apply {
    registerKeelApiModule()
      .registerKotlinModule()
      .registerULIDModule()
      .registerModule(JavaTimeModule())
      .configureSaneDateTimeRepresentation()
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
  }

private fun ObjectMapper.registerULIDModule(): ObjectMapper =
  registerModule(SimpleModule("ULID").apply {
    addSerializer(ULID.Value::class.java, ToStringSerializer())
    addDeserializer(ULID.Value::class.java, ULIDDeserializer())
  })

private fun ObjectMapper.configureSaneDateTimeRepresentation(): ObjectMapper =
  enable(WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(NON_NULL)
    .enable(WRITE_DATES_WITH_ZONE_ID)
    .enable(WRITE_DATE_KEYS_AS_TIMESTAMPS)
    .disable(WRITE_DURATIONS_AS_TIMESTAMPS)
    .apply {
      dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").apply {
        timeZone = TimeZone.getDefault()
      }
    }
