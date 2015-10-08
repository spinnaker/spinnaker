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

import groovy.transform.ToString

@ToString(includeNames = true)
class Location {
  enum Type {
    REGION,
    ZONE
  }
  Type type
  String value

  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false

    Location location = (Location) o

    if (type != location.type) return false
    if (value != location.value) return false

    return true
  }

  int hashCode() {
    int result
    result = (type != null ? type.hashCode() : 0)
    result = 31 * result + (value != null ? value.hashCode() : 0)
    return result
  }
}
