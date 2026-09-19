package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.netflix.spinnaker.keel.jackson.KeelApiModule
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.toSimpleLocations
import de.huxhorn.sulky.ulid.ULID
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.TimeZone
import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.MapperBuilder
import tools.jackson.databind.InjectableValues
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature.USE_NATIVE_TYPE_ID
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Factory method for [ObjectMapper]s configured how we like 'em.
 */
fun configuredObjectMapper(): JsonMapper = JsonMapper.builder()
  .configureForKeel()
  .build()

/**
 * Factory method for [YAMLMapper]s configured how we like 'em.
 */
fun configuredYamlMapper(): YAMLMapper = YAMLMapper.builder()
  .configureForKeel()
  .disable(USE_NATIVE_TYPE_ID)
  .build()

fun <M : ObjectMapper, B : MapperBuilder<M, B>> B.configureForKeel(): B {
  val kotlinModule = KotlinModule.Builder()
    .configure(KotlinFeature.NullToEmptyCollection, false)
    .configure(KotlinFeature.NullToEmptyMap, false)
    .configure(KotlinFeature.NullIsSameAsDefault, false)
    .configure(KotlinFeature.SingletonSupport, true)
    .configure(KotlinFeature.StrictNullChecks, false)
    .build()
  val ulidModule = SimpleModule("ULID").apply {
    addSerializer(ULID.Value::class.java, ToStringSerializer.instance)
    addDeserializer(ULID.Value::class.java, ULIDDeserializer())
  }
  val precisionModule = SimpleModule("Keel Java time").apply {
    addSerializer(Instant::class.java, PrecisionSqlSerializer())
  }

  return apply {
    addModule(KeelApiModule)
    addModule(kotlinModule)
    addModule(ulidModule)
    addModule(precisionModule)
    injectableValues(ContextAttributeInjectableValues())
    disable(FAIL_ON_UNKNOWN_PROPERTIES)
    enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
    changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL).withContentInclusion(NON_NULL) }
    enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
    enable(DateTimeFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS)
    disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
    defaultTimeZone(TimeZone.getTimeZone("UTC"))
    defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
  }
}

private class ContextAttributeInjectableValues : InjectableValues() {
  override fun findInjectableValue(
    context: DeserializationContext,
    valueId: Any?,
    forProperty: BeanProperty,
    beanInstance: Any?,
    optional: Boolean?,
    useInput: Boolean?
  ): Any? {
    if (valueId == null) return null
    val value = context.getAttribute(valueId).let {
      if (it is SubnetAwareLocations && forProperty.type.isTypeOrSubTypeOf(SimpleLocations::class.java)) {
        it.toSimpleLocations()
      } else {
        it
      }
    }
    if (value != null || optional == true) return value
    throw context.missingInjectableValueException(
      "No injectable value for '$valueId'",
      valueId,
      forProperty,
      beanInstance
    )
  }

  override fun snapshot(): InjectableValues = this
}
