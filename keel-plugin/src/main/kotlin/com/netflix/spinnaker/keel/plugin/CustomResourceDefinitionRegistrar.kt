package com.netflix.spinnaker.keel.plugin

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kubernetes.client.ApiException
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.Reader
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.text.SimpleDateFormat
import java.util.*

internal class CustomResourceDefinitionRegistrar(
  private val extensionsApi: ApiextensionsV1beta1Api,
  private val locator: () -> Reader
) {
  fun registerCustomResourceDefinition(): V1beta1CustomResourceDefinition =
    locator().let(::getOrCreate)

  private fun getOrCreate(reader: Reader): V1beta1CustomResourceDefinition {
    val parsed = try {
      mapper.readValue<V1beta1CustomResourceDefinition>(reader)
    } catch (e: JsonMappingException) {
      log.error("Error parsing CRD: {}", e.message)
      throw e
    }

    val name = parsed.metadata.name

    try {
      log.debug("Registering {}", name)
      return extensionsApi
        .createCustomResourceDefinition(parsed, "true")
        .also {
          log.debug("Registered CRD {}", mapper.writeValueAsString(it))

          // block until the CRD is registered for sure. TODO: do we actually need this?
          runBlocking {
            GlobalScope.launch {
              var _crd: V1beta1CustomResourceDefinition? = null
              while (_crd == null) {
                log.debug("Checking if CRD {} is registered yet", name)
                _crd = extensionsApi.getCustomResourceDefinition(name)
                if (_crd == null) delay(100)
              }
              log.debug("CRD {} is registered", name)
            }
              .join()
          }
        }
    } catch (e: ApiException) {
      if (e.code == HTTP_CONFLICT) {
        log.info("CRD {} is already registered", name)
        return extensionsApi.getCustomResourceDefinition(name)
          ?: throw IllegalStateException("CRD $name could not be registered, but can't be found either")
      } else {
        throw e
      }
    }
  }

  private val mapper by lazy {
    YAMLMapper()
      .registerKotlinModule()
      .registerModule(JavaTimeModule())
      .registerModule(JodaModule())
      .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
      .enable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS)
      .apply {
        dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").apply {
          timeZone = TimeZone.getDefault()
        }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

private fun ApiextensionsV1beta1Api.getCustomResourceDefinition(name: String): V1beta1CustomResourceDefinition? =
  try {
    readCustomResourceDefinition(
      name,
      "true",
      null,
      null
    )
  } catch (e: ApiException) {
    if (e.code == HTTP_NOT_FOUND) {
      null
    } else {
      throw e
    }
  }
