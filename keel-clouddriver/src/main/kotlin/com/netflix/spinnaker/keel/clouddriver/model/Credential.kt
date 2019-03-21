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
package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonCreator

data class Credential(
  val name: String,
  val type: String,
  @get:JsonAnyGetter val attributes: Map<String, Any?> = emptyMap()
) {
  @JsonCreator
  constructor(data: Map<String, Any?>) : this(
    name = data.getValue("name") as String,
    type = data.getValue("type") as String,
    attributes = data - "name" - "type"
  )
}
