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

package com.netflix.spinnaker.kork.secrets

import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * Default implementation for parsing secret references based on URIs. The [URI scheme][URI.getScheme] should match
 * the scheme given in the prefix. When using an opaque URI, the [scheme specific part][URI.getSchemeSpecificPart]
 * is what is parsed for the secret engine and parameters. When using a hierarchical URI, the
 * [authority][URI.getAuthority] is used for the secret engine and the [query string][URI.getQuery] is used for
 * the parameters.
 *
 * @param prefix the URI scheme prefix to look for such as `secret:` or `secret://`
 * @param parameterDelimiter the delimiter used to separate parameters in the URI
 * @param keyValueDelimiter the delimiter used to separate keys and values in parameters
 * @param type the expected type of URI to parse
 */
class SecretUriReferenceParser(
  private val prefix: String,
  private val parameterDelimiter: String,
  private val keyValueDelimiter: String,
  private val type: SecretUriType,
) : SecretReferenceParser {
  private val scheme = prefix.substringBefore(':')
  private val qPrefix = Pattern.quote(prefix)
  private val qParameterDelimiter = Pattern.quote(parameterDelimiter)
  private val qKeyValueDelimiter = Pattern.quote(keyValueDelimiter)
  private val opaqueFormat = Regex("$qPrefix.+($qParameterDelimiter[a-zA-Z0-9]+$qKeyValueDelimiter.+)+")

  override fun matches(input: String): Boolean = input.startsWith(prefix) && when (type) {
    SecretUriType.OPAQUE -> opaqueFormat.matches(input)
    SecretUriType.HIERARCHICAL -> runCatching { URI(input) }.isSuccess
  }

  override fun parse(input: String): ParsedSecretReference {
    val uri = try {
      URI(input)
    } catch (e: URISyntaxException) {
      throw InvalidSecretFormatException("Invalid $prefix URI", e).also {
        it.additionalAttributes["input"] = input
      }
    }
    if (uri.scheme != scheme) {
      throw InvalidSecretFormatException("Invalid $prefix URI scheme '$scheme'", uri)
    }

    val (engine, parameters) = when (type) {
      // example: suppose parameterDelimiter is & and keyValueDelimiter is =

      SecretUriType.OPAQUE -> {
        // example uri = scheme:engine&p=example&s=example
        val schemeSpecificPart = uri.rawSchemeSpecificPart
        val parameters = schemeSpecificPart.split(parameterDelimiter)
        if (parameters.size < 2) {
          throw InvalidSecretFormatException("Missing parameters in $prefix URI", uri)
        }
        val engine = parameters[0]
        engine to parameters.subList(1, parameters.size)
      }

      SecretUriType.HIERARCHICAL -> {
        // example uri = scheme://engine?p=example&s=example
        val engine =
          uri.authority ?: throw InvalidSecretFormatException("Missing secret engine identifier in $prefix URI", uri)
        val query =
          uri.query ?: throw InvalidSecretFormatException("Missing parameters in $prefix URI", uri)
        val parameters = query.split(parameterDelimiter)
        engine to parameters
      }
    }

    if (engine.isEmpty()) {
      throw InvalidSecretFormatException("Missing secret engine identifier in $prefix URI", uri)
    }
    if (parameters.isEmpty()) {
      throw InvalidSecretFormatException("Missing parameters in $prefix URI", uri)
    }

    val parsed = linkedMapOf<String, String>()
    for (parameter in parameters) {
      val keyValue = parameter.split(keyValueDelimiter, limit = 2)
      if (keyValue.size != 2) {
        throw InvalidSecretFormatException(
          "Invalid parameter in $prefix URI: '$parameter' is missing $keyValueDelimiter delimiter", uri
        )
      }
      val (key, value) = keyValue
      if (key in parsed) {
        throw InvalidSecretFormatException("Duplicate parameter key in $prefix URI: '$key'", uri)
      }
      parsed[key] = value
    }

    return ParsedSecretReference(scheme, engine, parsed)
  }
}
