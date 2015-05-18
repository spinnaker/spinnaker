/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.front50.model.application

class ApplicationPropertiesTransformer {
  /**
   * This method transforms the application properties to prepare them for creation or updating.
   * At present, it simply lowercases the application name.
   *
   * @param application the application properties to transform.
   * @return the same properties map that was passed in.
   */
  Map<String, String> transformApplicationProperties(Map<String, String> properties) {
    if (properties.name) {
      properties.name = properties.name.toLowerCase()
    }

    properties
  }
}
