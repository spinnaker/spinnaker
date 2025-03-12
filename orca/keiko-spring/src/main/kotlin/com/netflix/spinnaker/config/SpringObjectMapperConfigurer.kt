/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.exception.InvalidSubtypeConfigurationException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ClassUtils

/**
 * Automates registering subtypes to the queue object mapper by classpath scanning.
 */
class SpringObjectMapperConfigurer(
  private val properties: ObjectMapperSubtypeProperties
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun registerSubtypes(mapper: ObjectMapper) {
    registerSubtypes(mapper, properties.messageRootType, properties.messagePackages)
    registerSubtypes(mapper, properties.attributeRootType, properties.attributePackages)

    properties.extraSubtypes.entries.forEach {
      registerSubtypes(mapper, it.key, it.value)
    }
  }

  private fun registerSubtypes(
    mapper: ObjectMapper,
    rootType: String,
    subtypePackages: List<String>
  ) {
    getRootTypeClass(rootType).also { cls ->
      subtypePackages.forEach { mapper.registerSubtypes(*findSubtypes(cls, it)) }
    }
  }

  private fun getRootTypeClass(name: String): Class<*> {
    return ClassUtils.resolveClassName(name, ClassUtils.getDefaultClassLoader())
  }

  private fun findSubtypes(clazz: Class<*>, pkg: String): Array<NamedType> =
    ClassPathScanningCandidateComponentProvider(false)
      .apply { addIncludeFilter(AssignableTypeFilter(clazz)) }
      .findCandidateComponents(pkg)
      .map {
        check(it.beanClassName != null)
        val cls = ClassUtils.resolveClassName(it.beanClassName, ClassUtils.getDefaultClassLoader())

        // Enforce all implementing types to have a JsonTypeName class
        val serializationName = cls.annotations
          .filterIsInstance<JsonTypeName>()
          .firstOrNull()
          ?.value
          ?: throw InvalidSubtypeConfigurationException(
            "Subtype ${cls.simpleName} does not have a JsonTypeName"
          )

        log.info("Registering subtype of ${clazz.simpleName}: $serializationName")
        return@map NamedType(cls, serializationName)
      }
      .toTypedArray()
}
