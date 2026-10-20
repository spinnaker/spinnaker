/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.keel.jackson

import java.util.concurrent.ConcurrentHashMap
import tools.jackson.databind.JacksonModule.SetupContext
import tools.jackson.databind.jsontype.NamedType
import tools.jackson.databind.module.SimpleModule

/** Retains extension registrations so Jackson 3 mapper rebuilds can replay them. */
class ExtensionSubtypeModule : SimpleModule("Keel extension subtypes") {
  private val subtypes = ConcurrentHashMap<Pair<Class<*>, String>, NamedType>()

  fun register(baseType: Class<*>, subtype: NamedType) {
    subtypes[baseType to subtype.name] = subtype
  }

  override fun setupModule(context: SetupContext) {
    super.setupModule(context)
    context.registerSubtypes(*subtypes.values.toTypedArray())
  }
}
