/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline.model.support

import com.fasterxml.jackson.databind.JsonNode
import com.netflix.spinnaker.orca.pipeline.model.Trigger

/**
 * Provides a [predicate] & [deserializer] pair for custom trigger types.
 *
 * The [predicate] will return true if the [deserializer] should be used
 * for the provided JsonNode. If more than one [predicate] returns true,
 * the first supplier will be chosen.
 */
interface CustomTriggerDeserializerSupplier {
  val predicate: (node: JsonNode) -> Boolean
  val deserializer: (node: JsonNode) -> Trigger
}
