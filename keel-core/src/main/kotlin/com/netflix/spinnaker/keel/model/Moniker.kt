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
package com.netflix.spinnaker.keel.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.keel.api.Moniker

/**
 * Returns the moniker-compliant name without [Moniker.sequence].
 * This is suitable for clusters or resource types with no versioning.
 */
val Moniker.name: String
  get() = when {
    stack == null && detail == null -> app
    detail == null -> "$app-$stack"
    else -> "$app-${stack.orEmpty()}-$detail"
  }

/**
 * Returns the moniker-compliant name with [Moniker.sequence] if it is non-`null`.
 * This is suitable for server groups.
 */
val Moniker.serverGroup: String
  get() = when {
    sequence == null -> name
    stack == null && detail == null -> "$app-v$sequenceString"
    detail == null -> "$app-$stack-v$sequenceString"
    else -> "$app-${stack.orEmpty()}-$detail-v$sequenceString"
  }

/**
 * Returns the moniker-compliant name in the format used by Orca.
 */
val Moniker.orcaClusterMoniker: Map<String, Any?>
  get() = mapOf(
    "app" to app,
    "stack" to stack,
    "detail" to detail,
    "cluster" to name,
    "sequence" to sequence
  )
    .filterValues { it != null }

/**
 * Converts [Moniker.sequence] to a string with appropriate padding.
 */
private val Moniker.sequenceString: String?
  get() = sequence?.rem(1000)?.toString()?.padStart(3, '0')

/**
 * Converts a moniker-compliant name into a [Moniker] object.
 */
fun parseMoniker(name: String): Moniker =
  Names.parseName(name).let {
    Moniker(
      it.app,
      it.stack,
      it.detail,
      it.sequence
    )
  }
