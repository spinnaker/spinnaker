/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.controllers.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.ATTRIBUTE
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.INTENT
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.INTENT_SPEC
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.POLICY
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.POLICY_SPEC
import com.netflix.spinnaker.keel.controllers.v1.SchemaController.SchemaType.values
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.policy.Policy
import com.netflix.spinnaker.keel.policy.PolicySpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ClassUtils
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import javax.ws.rs.QueryParam

/**
 * TODO rz - Better querying / indexing ability
 * TODO rz - Computed annotation needs to inject computed schema property
 * TODO rz - Polymorphism doesn't inherit schema property descriptions
 */
@RestController
@RequestMapping("/v1/schemas")
class SchemaController
@Autowired constructor(
  private val objectMapper: ObjectMapper
) {

  private val generator = JsonSchemaGenerator(objectMapper)

  @RequestMapping(value = "/{type}", method = arrayOf(RequestMethod.GET))
  fun getSchemas(@PathVariable("type") type: String, @QueryParam("name") name: String?): String {
    if (name != null) {
      return objectMapper.writeValueAsString(generator.generateJsonSchema(
        findSchemaByName(typeFromString(type), name)
      ))
    }
    return objectMapper.writeValueAsString(generator.generateJsonSchema(typeFromString(type)))
  }

  private fun typeFromString(type: String) =
    when(type) {
      INTENT.toString() -> INTENT.klass
      INTENT_SPEC.toString() -> INTENT_SPEC.klass
      ATTRIBUTE.toString() -> ATTRIBUTE.klass
      POLICY.toString() -> POLICY.klass
      POLICY_SPEC.toString() -> POLICY_SPEC.klass
      else -> throw DeclarativeException("Unknown schema type: $type (must be one of ${values().map { it.name }})")
    }

  private fun findSchemaByName(klass: Class<*>, name: String): Class<*>? {
    return ClassPathScanningCandidateComponentProvider(false)
      .apply { addIncludeFilter(AssignableTypeFilter(klass)) }
      .findCandidateComponents("com.netflix.spinnaker.keel")
      .map { ClassUtils.resolveClassName(it.beanClassName, ClassUtils.getDefaultClassLoader()) }
      .firstOrNull { it.simpleName == name }
  }

  private enum class SchemaType(val klass: Class<*>) {
    INTENT(Intent::class.java),
    INTENT_SPEC(IntentSpec::class.java),
    ATTRIBUTE(Attribute::class.java),
    POLICY(Policy::class.java),
    POLICY_SPEC(PolicySpec::class.java)
  }
}
