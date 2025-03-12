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
 */

package com.netflix.spinnaker.igor.gcb

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.cloudbuild.v1.model.Build

class GoogleCloudBuildTestSerializationHelper {
  private static ObjectMapper objectMapper = new ObjectMapper()

  @SuppressWarnings("unchecked")
  static final Map serializeBuild(Build inputBuild) {
    // com.google.api.services.cloudbuild.v1.model.StorageSource.generation is of type Long
    // but is annotated with @com.google.api.client.json.JsonString. This causes
    // Jackson to throw an error when converting from a serialized Map to a Build.
    // Force a String value to prevent this problem.
    Map value = objectMapper.convertValue(inputBuild, Map.class)
    if (value.containsKey("source")) {
      Map source = (Map) value.get("source")
      if (source.containsKey("storageSource")) {
        Map storageSource = (Map) source.get("storageSource")
        Long generation = (Long) storageSource.get("generation")
        storageSource.put("generation", generation.toString())
      }
    }
    return value
  }
}
