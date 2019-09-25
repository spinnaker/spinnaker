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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

@JsonInclude(NON_NULL)
data class Moniker(
  val app: String,
  val stack: String? = null,
  val detail: String? = null,
  val sequence: String? = null,
  val cluster: String? = null
) {
  @get:JsonIgnore
  val name: String
    get() = when {
      stack == null && detail == null -> app
      detail == null -> "$app-$stack"
      else -> "$app-${stack.orEmpty()}-$detail"
    }

  @get:JsonIgnore
  val serverGroup: String
    get() = when {
      stack == null && detail == null -> "$app-v$sequence"
      detail == null && sequence != null -> "$app-$stack-v$sequence"
      else -> "$app-${stack.orEmpty()}-$detail-v$sequence"
    }
}
