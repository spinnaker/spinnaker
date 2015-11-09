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

package com.netflix.spinnaker.orca.clouddriver.pipeline.support

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Immutable
import groovy.transform.ToString

@ToString(includeNames = true)
@Immutable
class Location {
  enum Type {
    REGION,
    ZONE
  }
  Type type
  String value

  /**
   * @return The all lowercase, plural form of this location type ("regions" or "zones")
   */
  @JsonIgnore
  String pluralType() {
    return this.type.toString().toLowerCase() + "s"
  }

  /**
   * @return The all lowercase, singular form of this location type ("region" or "zone")
   */
  @JsonIgnore
  String singularType() {
    return this.type.toString().toLowerCase()
  }
}
