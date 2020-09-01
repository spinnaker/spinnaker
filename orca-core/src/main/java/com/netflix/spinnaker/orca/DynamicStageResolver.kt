/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.orca

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import org.slf4j.LoggerFactory

/**
 * Allows for multiple stages to be wired up with the same alias, using dynamic config to dictate which to use.
 *
 * This class makes migrating stages originally written directly into Orca to a plugin model easier. To prevent stage
 * "downtime", a stage will be included in Orca as well as by a plugin, both of which will share one or more aliases.
 * Duplicate safety checks are still performed: If a config does not exist for a [StageDefinitionBuilder.getType] or
 * its alias, exceptions will be thrown, which will cause the application to not start.
 *
 * This resolver is more expensive than [DefaultStageResolver], so unless you are migrating stages, usage of this
 * [StageResolver] is not recommended.
 *
 * The config values follow a convention of `dynamic-stage-resolver.${stageDefinitionBuilderAlias}`, where the value
 * should be the canonical class name of the desired [StageDefinitionBuilder]. Having two builders with the same alias
 * and canonical class name is never allowed.
 */
class DynamicStageResolver(
  private val dynamicConfigService: DynamicConfigService,
  stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : StageResolver {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val stageDefinitionBuildersByAlias: MutableMap<String, MutableList<StageDefinitionBuilder>> = mutableMapOf()
  private val fallbackPreferences: MutableMap<String, String> = mutableMapOf()

  init {
    stageDefinitionBuilders.forEach { builder ->
      putOrAdd(builder.type, builder)
      builder.aliases().forEach { alias ->
        putOrAdd(alias, builder)
      }
    }

    stageDefinitionBuildersByAlias.filter { it.value.size > 1 }.also {
      validatePreferences(it)
      validateClassNames(it)
      cachePreferences(it)
    }
  }

  override fun getStageDefinitionBuilder(type: String, typeAlias: String?): StageDefinitionBuilder {
    var builder: StageDefinitionBuilder? = null

    val builderForType = stageDefinitionBuildersByAlias[type]
    if (builderForType != null) {
      builder = builderForType.resolveByPreference(type)
    }

    if (builder == null && typeAlias != null) {
      builder = stageDefinitionBuildersByAlias[typeAlias]?.resolveByPreference(typeAlias)
    }

    if (builder == null) {
      throw StageResolver.NoSuchStageDefinitionBuilderException(type, typeAlias, stageDefinitionBuildersByAlias.keys)
    }

    return builder
  }

  private fun putOrAdd(key: String, stageDefinitionBuilder: StageDefinitionBuilder) {
    stageDefinitionBuildersByAlias.computeIfAbsent(key) { mutableListOf() }.add(stageDefinitionBuilder)
  }

  /**
   * Ensures that any conflicting [StageDefinitionBuilder] keys have config set to resolve the preferred instance.
   */
  private fun validatePreferences(duplicates: Map<String, MutableList<StageDefinitionBuilder>>) {
    duplicates.forEach { duplicate ->
      val pref = getPreference(duplicate.key)

      if (pref == NO_PREFERENCE) {
        throw NoPreferenceConfigPresentException(duplicate.key)
      }

      // Ensure the preference is actually valid: Is there a StageDefinitionBuilder with a matching canonical name?
      duplicate.value.map { it.extensionClass.canonicalName }.let {
        if (!it.contains(pref)) {
          throw InvalidStageDefinitionBuilderPreference(duplicate.key, pref, it)
        }
      }
    }
  }

  /**
   * Ensures that no conflicting [StageDefinitionBuilder]s have the same canonical class name.
   */
  private fun validateClassNames(duplicates: Map<String, MutableList<StageDefinitionBuilder>>) {
    duplicates
      .filter { entry ->
        entry.value.map { it.extensionClass.canonicalName }.distinct().size != entry.value.size
      }
      .also {
        if (it.isNotEmpty()) {
          throw ConflictingClassNamesException(it.keys)
        }
      }
  }

  /**
   * Caches all preferences for fallback if a specific dynamic config becomes invalid later. It is preferable to use
   * an old value rather than throwing a runtime exception because no [StageDefinitionBuilder] could be located.
   */
  private fun cachePreferences(duplicates: Map<String, MutableList<StageDefinitionBuilder>>) {
    duplicates.forEach {
      fallbackPreferences[it.key] = getPreference(it.key)
    }
  }

  /**
   * Locates the [StageDefinitionBuilder] for a given [type], falling back to the original defaults if the config has
   * changed to an invalid value.
   */
  private fun List<StageDefinitionBuilder>.resolveByPreference(type: String): StageDefinitionBuilder? {
    if (isEmpty()) {
      return null
    }

    if (size == 1) {
      return first()
    }

    val pref = getPreference(type)
    val builder = firstOrNull { it.extensionClass.canonicalName == pref }
    if (builder == null && fallbackPreferences.containsKey(type)) {
      log.warn("Preference for '$type' ($pref) is invalid, falling back to '${fallbackPreferences[type]}'")
      return firstOrNull { it.extensionClass.canonicalName == fallbackPreferences[type] }
    }

    return builder
  }

  private fun getPreference(type: String): String =
    dynamicConfigService.getConfig(
      String::class.java,
      "dynamic-stage-resolver.$type",
      NO_PREFERENCE
    )

  internal inner class NoPreferenceConfigPresentException(key: String) : SystemException(
    "No DynamicStageResolver preference config set for conflicting StageDefinitionBuilder of type '$key'"
  )

  internal inner class ConflictingClassNamesException(keys: Set<String>) : SystemException(
    "Conflicting StageDefinitionBuilder class names for keys: ${keys.joinToString()}"
  )

  internal inner class InvalidStageDefinitionBuilderPreference(
    key: String,
    pref: String,
    candidates: List<String>
  ) : SystemException(
    "Preference for '$key' StageDefinitionBuilder of '$pref' is invalid. " +
      "Valid canonical names are: ${candidates.joinToString()}"
  )

  private companion object {
    const val NO_PREFERENCE = "no-preference-config-defined"
  }
}
