/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.libdiffs

class Library {
  String filePath
  String name
  String version
  String org
  String status
  String buildDate

  Library(String filePath, String name, String version, String org, String status) {
    this.filePath = filePath
    this.name = name
    this.version = version
    this.org = org
    this.buildDate = buildDate
    this.status = status
  }

  boolean equals(o) {
    if (this.is(o)) return true
    if (!(o instanceof Library)) return false

    Library library = (Library) o

    if (name != library.name) return false

    return true
  }

  int hashCode() {
    int result
    result = (name != null ? name.hashCode() : 0)
    return result
  }
}
