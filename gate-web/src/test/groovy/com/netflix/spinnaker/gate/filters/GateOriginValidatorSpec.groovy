/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.filters

import spock.lang.Specification

/**
 * GateOriginValidatorSpec.
 */
class GateOriginValidatorSpec extends Specification {

  void "#desc isValidOrigin #expected"() {
    given:
    def validator = new GateOriginValidator('http://localhost:9000', null, null)

    expect:
    validator.isValidOrigin(origin) == expected

    where:
    origin                  | expected | desc
    null                    | false    | 'null origin'
    ''                      | false    | 'empty origin'
    '/foo'                  | false    | 'not absolute URI'
    'file:/bar'             | false    | 'no host component'
    'http://localhost:9000' | true     | 'match for configured deckUri'
  }

  void "uses allowedOrigins if configured #desc"() {
    given:
    def validator = new GateOriginValidator('http://localhost:9000', 'evil.com', /^http(s)?:\/\/good.com:7777(\/)?$/)

    expect:
    validator.isValidOrigin(origin) == expected

    where:
    origin                   | expected | desc
    'http://localhost:9000'  | false    | 'deckUri'
    'http://evil.com'        | false    | 'redirectHosts'
    'http://good.com:7777'   | true     | 'optional path'
    'http://good.com:7777/'  | true     | 'with path'
    'https://good.com:7777/' | true     | 'with https'
    'http://good.com:666'    | false    | 'port mismatch'
  }

  void "wildcard domain example"() {
    given:
    def validator = new GateOriginValidator("http://foo", null, /^https?:\/\/(?:localhost|[^\/]+\.example\.com)(?::[1-9]\d*)?\/?/)

    expect:
    validator.isValidOrigin(origin) == expected

    where:
    origin                                                                    | expected
    'http://localhost:9000'                                                   | true
    'http://www.example.com'                                                  | true
    'http://www.example.com:80/'                                              | true
    'https://www.example.com'                                                 | true
    'https://www.example.com:443/'                                            | true
    'https://www.example.com:/'                                               | false
    'https://www.example.com:0/'                                              | false
    'https://www.example.com:08/'                                             | false
    'https://www.evil.com' + URLEncoder.encode('/', 'UTF-8') + '.example.com' | false
    'https://www.evil.com/.example.com/'                                      | false
  }
}
