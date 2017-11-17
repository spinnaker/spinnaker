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
package com.netflix.spinnaker.keel

import org.slf4j.Logger
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ClassUtils

fun findAllSubtypes(log: Logger, clazz: Class<*>, pkg: String): Array<Class<*>>
  = ClassPathScanningCandidateComponentProvider(false)
    .apply { addIncludeFilter(AssignableTypeFilter(clazz)) }
    .findCandidateComponents(pkg)
    .map {
      val cls = ClassUtils.resolveClassName(it.beanClassName, ClassUtils.getDefaultClassLoader())
      log.info("Registering ${cls.simpleName}")
      return@map cls
    }
    .toTypedArray()
