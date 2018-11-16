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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import java.nio.charset.Charset

/**
 * Internal representation of an asset.
 */
data class Asset<T : Any>(
  val apiVersion: ApiVersion,
  val kind: String, // TODO: create a type
  val metadata: AssetMetadata,
  val spec: T
)

private val mapper by lazy { YAMLMapper().registerKotlinModule() }

val <T : Any> Asset<T>.fingerprint: HashCode
  get() {
    return Hashing
      .murmur3_128()
      .hashString(
        mapper.writeValueAsString(spec),
        Charset.forName("UTF-8")
      )
  }

val <T : Any> Asset<T>.id: AssetName
  get() = metadata.name
