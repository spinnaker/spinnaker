/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.compression

import spock.lang.Specification

class GZipCompressionStrategySpec extends Specification {

  def 'should compress and decompress values'() {
    given:
    def subject = new GZipCompression(16, true)

    when:
    def result = subject.compress(data)

    then:
    if (shouldCompress) {
      result != data
    } else {
      result == data
    }
    subject.decompress(result) == data

    where:
    data          || shouldCompress
    'hello world' || true
    'foo bar baz' || true
    'a'           || false
  }
}
