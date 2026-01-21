/*
 * Copyright 2024 Apple, Inc.
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

package com.netflix.spinnaker.kork.secrets.env

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException
import com.netflix.spinnaker.kork.secrets.SecretReferenceParser
import com.netflix.spinnaker.kork.secrets.SecretReferenceResolver
import com.netflix.spinnaker.kork.secrets.SecretUriReferenceParser
import com.netflix.spinnaker.kork.secrets.SecretUriType
import com.netflix.spinnaker.kork.secrets.StandardSecretParameter
import org.springframework.boot.json.JacksonJsonParser
import org.springframework.core.env.CompositePropertySource
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.PropertySources
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * Provides resolution of secret data for properties.
 */
class SecretReferenceResolvers(
  private val secretReferenceResolvers: Collection<SecretReferenceResolver>,
  private val secretReferenceParser: SecretReferenceParser = SecretUriReferenceParser(
    "boot-secret://",
    "&",
    "=",
    SecretUriType.HIERARCHICAL
  ),
  private val secretParser: (String) -> Map<String, Any> = JacksonJsonParser(YAMLMapper())::parseMap,
) {

  /**
   * Resolves all secret values in the given property sources and installs the results as a new
   * top priority property source.
   */
  fun registerResolvedSecrets(sources: MutablePropertySources) {
    sources.remove(PROPERTY_SOURCE)
    if (secretReferenceResolvers.isEmpty()) return
    // logic adapted from AbstractEnvironmentDecrypt in spring-cloud-context
    val properties = sources.merge().also { it.replaceAll { _, value -> resolveSecretIfPresent(value) } }
    if (properties.isNotEmpty()) {
      // insert at highest priority so resolved properties take precedence
      sources.addFirst(SystemEnvironmentPropertySource(PROPERTY_SOURCE, properties))
    }
  }

  /**
   * Resolves a secret value if present using the configured [SecretReferenceParser] and
   * [SecretReferenceResolver] instances. Supports [StandardSecretParameter.KEY] in which case the
   * secret payload is parsed as YAML and the requested key is used to find a value in the parsed map.
   *
   * @return the resolved secret value if the original value was a supported secret reference or the original value otherwise
   * @throws SecretDecryptionException if a requested key cannot be parsed or found from the secret payload
   */
  fun resolveSecretIfPresent(value: Any): Any {
    if (value !is String || !secretReferenceParser.matches(value)) return value
    val reference = secretReferenceParser.parse(value)
    val resolver = secretReferenceResolvers.firstOrNull { it.supports(reference) } ?: return value
    val resolved = resolver.resolve(reference)
    val key = reference.getParameter(StandardSecretParameter.KEY) ?: return resolved
    return runCatching { secretParser(resolved) }
      .onFailure {
        throw SecretDecryptionException("Unable to parse secret payload", it).apply {
          additionalAttributes["secretReference"] = reference
          additionalAttributes["secretKey"] = key
        }
      }
      .map {
        it[key] ?: throw SecretDecryptionException("No value found for key").apply {
          additionalAttributes["secretReference"] = reference
          additionalAttributes["secretKey"] = key
        }
      }
      .getOrThrow()
  }

  private fun PropertySources.merge(): MutableMap<String, Any> {
    val properties = linkedMapOf<String, Any>()
    reversed().forEach { it.mergeTo(properties) }
    return properties
  }

  private fun PropertySource<*>.mergeTo(properties: MutableMap<String, Any>) {
    when (this) {
      is CompositePropertySource -> mergeTo(properties)
      is EnumerablePropertySource<*> -> mergeTo(properties)
    }
  }

  private fun CompositePropertySource.mergeTo(properties: MutableMap<String, Any>) {
    propertySources.reversed().forEach { it.mergeTo(properties) }
  }

  private fun EnumerablePropertySource<*>.mergeTo(properties: MutableMap<String, Any>) {
    val otherCollectionProperties = linkedMapOf<String, Any>()
    var sourceHasDecryptedCollection = false
    for (propertyName in propertyNames) {
      val property = getProperty(propertyName)
      if (property !is String) continue
      if (secretReferenceParser.matches(property)) {
        if (collectionPropertyName.matches(propertyName)) {
          sourceHasDecryptedCollection = true
        }
        properties[propertyName] = property
      } else if (collectionPropertyName.matches(propertyName)) {
        // put unprocessed properties so merging of index properties happens correctly
        otherCollectionProperties[propertyName] = property
      } else {
        // override previously encrypted with non-encrypted property
        properties.remove(propertyName)
      }
    }
    // copy all indexed properties even if not encrypted
    if (sourceHasDecryptedCollection && otherCollectionProperties.isNotEmpty()) {
      properties += otherCollectionProperties
    }
  }

  companion object {
    private val collectionPropertyName = Regex("(\\S+)?\\[(\\d+)](\\.\\S+)?")
    private const val PROPERTY_SOURCE = "resolvedSecrets"
  }
}
