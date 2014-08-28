/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos

import spock.lang.Specification
import spock.lang.Subject

class YamlAccountCredentialsFactorySpec extends Specification {

  @Subject factory = new YamlAccountCredentialsFactory()

  void "should convert yaml from string to typed object"() {
    given:
      def obj = factory.load("foo: blah\nbar: bleh", AccountConfig)

    expect:
      obj instanceof AccountConfig
      obj.foo == "blah"
      obj.bar == "bleh"
  }

  void "should load yaml from classpath to typed object"() {
    given:
      def obj = factory.load(getClass().getResourceAsStream("/account.yml"), AccountConfig)

    expect:
      obj instanceof AccountConfig
      obj.foo == "blah"
      obj.bar == "bleh"
  }

  void "should load yaml from file to typed object"() {
    setup:
      def f = File.createTempFile("amos", ".yml")
      f << "foo: blah\nbar: bleh".bytes

    when:
      def obj = factory.load(f, AccountConfig)

    then:
      obj instanceof AccountConfig
      obj.foo == "blah"
      obj.bar == "bleh"
  }

  static class AccountConfig {
    String foo
    String bar
  }
}
