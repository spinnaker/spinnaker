/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

@JsonSerialize(using = ToStringSerializer::class)
data class ApiVersion(
  val group: String,
  val version: String
) {
  @JsonCreator
  constructor(value: String) :
    this(value.substringBefore("/"), value.substringAfter("/"))

  override fun toString() = "$group/$version"

  fun subApi(prefix: String) = copy(group = "$prefix.$group")

  @JsonIgnore
  val prefix: String = group.substringBefore(".")
}

val SPINNAKER_API_V1 = ApiVersion("spinnaker.netflix.com", "v1")
