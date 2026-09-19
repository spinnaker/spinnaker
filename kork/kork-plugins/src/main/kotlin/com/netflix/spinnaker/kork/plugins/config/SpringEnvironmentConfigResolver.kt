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

import tools.jackson.core.type.TypeReference
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.MissingNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.TreeTraversingParser
import tools.jackson.dataformat.javaprop.JavaPropsMapper
import tools.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import java.lang.RuntimeException
import java.lang.reflect.ParameterizedType
import java.util.Properties
import org.slf4j.LoggerFactory
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource

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
@Beta
class SpringEnvironmentConfigResolver(
  private val environment: ConfigurableEnvironment
) : ConfigResolver {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val mapper: ObjectMapper = JsonMapper.builder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .addModule(KotlinModule.Builder().build())
    .build()

  override fun <T> resolve(coordinates: ConfigCoordinates, expectedType: Class<T>): T =
    resolveInternal(coordinates, { mapper.convertValue(emptyMap<Any, Any>(), expectedType) }) {
      mapper.readValue(it, expectedType)
    }

  override fun <T> resolve(coordinates: ConfigCoordinates, expectedType: TypeReference<T>): T =
    resolveInternal(
      coordinates,
      {
        // Yo, it's fine. Totally fine.
        @Suppress("UNCHECKED_CAST")
        val type = ((expectedType.type as ParameterizedType).rawType as Class<T>)
        if (type.isInterface) {
          // TODO(rz): Maybe this should be supported, but there's only one use at this point, and we can just call in
          //  with HashMap instead of Map.
          throw SystemConfigException("Expected type must be a concrete class, interface given")
        }
        mapper.convertValue(emptyMap<Any, Any>(), type)
      }
    ) {
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

    // Boot 4 binds empty YAML maps (e.g. `repositories: {}`) as empty-string
    // properties where Boot 3 dropped them entirely; treat both as missing.
    if (tree is MissingNode || (tree.isTextual && tree.asText().isEmpty())) {
      log.debug("Missing configuration for '$coordinates': Loading default")
      return missingCallback()
    }

    log.debug("Found config at '$pointer'")

    try {
      return callback(TreeTraversingParser(tree))
    } catch (pe: StreamReadException) {
      throw IntegrationException("Failed reading extension config: Input appears invalid", pe)
    } catch (me: DatabindException) {
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
      .filter{ (_,value)-> if (value is Map<*, *>) value.isNotEmpty() else true }
      .toMap()

  private inner class SystemConfigException(message: String) : SystemException(message)
}
