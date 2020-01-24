/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins.config

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.annotations.Alpha
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.slf4j.LoggerFactory
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import java.lang.reflect.ParameterizedType
import java.lang.RuntimeException
import java.util.Properties

/**
 * Resolves extension config from the parent Spring Environment.
 *
 * The actual Spring code that handles ConfigurationProperties is not available for use outside of Spring, so we have
 * to use Jackson. This is done by finding all [EnumerablePropertySource] property sources and converting their
 * backing sources into a nested map, then casting that resulting map into the desired config type. We're restricted
 * to only [EnumerablePropertySource], as not all property sources know what properties they actually have. Depending
 * on the configuration of property sources, this may cause unexpected config shapes.
 *
 * TODO(rz): Should introduce some mechanism for providing plugins updated configuration in the case of backing
 *  plugin configuration with Spring Config Server / FastProps, without leaking Spring into the plugins.
 */
@Alpha
class SpringEnvironmentConfigResolver(
  private val environment: ConfigurableEnvironment
) : ConfigResolver {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val mapper = ObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .registerModules(KotlinModule())

  override fun <T> resolve(coordinates: ConfigCoordinates, expectedType: Class<T>): T =
    resolveInternal(coordinates, { expectedType.newInstance() }) {
      mapper.readValue(it, expectedType)
    }

  override fun <T> resolve(coordinates: ConfigCoordinates, expectedType: TypeReference<T>): T =
    resolveInternal(coordinates, {
      // Yo, it's fine. Totally fine.
      @Suppress("UNCHECKED_CAST")
      val type = ((expectedType.type as ParameterizedType).rawType as Class<T>)
      if (type.isInterface) {
        // TODO(rz): Maybe this should be supported, but there's only one use at this point, and we can just call in
        //  with HashMap instead of Map.
        throw SystemConfigException("Expected type must be a concrete class, interface given")
      }
      type.newInstance() as T
    }) {
      mapper.readValue(it, expectedType)
    }

  private fun <T> resolveInternal(
    coordinates: ConfigCoordinates,
    missingCallback: () -> T,
    callback: (TreeTraversingParser) -> T
  ): T {
    val pointer = coordinates.toPointer()
    log.debug("Searching for config at '$pointer'")

    val tree = mapper.valueToTree<ObjectNode>(propertySourcesAsMap()).at(pointer)

    if (tree is MissingNode) {
      log.debug("Missing configuration for '$coordinates': Loading default")
      return missingCallback()
    }

    try {
      return callback(TreeTraversingParser(tree, mapper))
    } catch (pe: JsonParseException) {
      throw IntegrationException("Failed reading extension config: Input appears invalid", pe)
    } catch (me: JsonMappingException) {
      throw IntegrationException("Failed reading extension config: Could not map provided config to expected shape", me)
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
      throw SystemException("Failed resolving extension config for an unexpected reason", e)
    }
  }

  private fun propertySourcesAsMap(): Map<*, *> {
    return environment.propertySources.reversed()
      .filterIsInstance<EnumerablePropertySource<*>>()
      .fold(mutableMapOf<String, Any?>()) { acc, ps ->
        acc.putAll(ps.toRelevantProperties())
        acc
      }
      .let { Properties().apply { putAll(it) } }
      .let { JavaPropsMapper().readPropertiesAs(it, Map::class.java) }
  }

  /**
   * Filters out the configs that we don't care about and converts the config properties into a Map
   */
  private fun EnumerablePropertySource<*>.toRelevantProperties(): Map<String, Any?> =
    propertyNames
      .filter { it.startsWith("spinnaker.extensibility") }
      .map { it to getProperty(it) }
      .toMap()

  inner class SystemConfigException(message: String) : SystemException(message)
}
