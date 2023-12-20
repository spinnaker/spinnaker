/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.security

import spock.lang.Specification

class AuthorizationSpec extends Specification {
  void "parse returns appropriate value"() {
    expect:
    Authorization.parse(input) == output
    where:
    input                      || output
    null                       || null
    'read'                     || Authorization.READ
    'READ'                     || Authorization.READ
    "Read"                     || Authorization.READ
    Authorization.READ         || Authorization.READ
    'write'                    || Authorization.WRITE
    "${Authorization.EXECUTE}" || Authorization.EXECUTE
    'create'                   || Authorization.CREATE
    'unsupported'              || null
    ' read '                   || null
    'query'                    || null
  }
}
