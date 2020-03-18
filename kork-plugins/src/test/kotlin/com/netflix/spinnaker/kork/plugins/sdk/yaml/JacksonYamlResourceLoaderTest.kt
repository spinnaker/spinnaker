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

package com.netflix.spinnaker.kork.plugins.sdk.yaml

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.message

class JacksonYamlResourceLoaderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("Load a YML resource") {

      test("loading yml file that does not exist") {
        expectThrows<IllegalArgumentException> {
          subject.loadResource("unknown.yml", HashMap<String, String>().javaClass)
        }.and {
          message.isEqualTo("Cannot load specified resource: unknown.yml , for extension: Fixture")
        }
      }

      test("loading yml file that does exist") {

        expectThat(subject.loadResource("yaml/test.yml", HashMap<String, String>().javaClass))
          .isA<Map<String, String>>()
          .and {
            hasSize(1)
            get("name").isEqualTo("hello")
          }
      }

      test("loading yml file that does exist and return a object") {

        expectThat(subject.loadResource("yaml/sampleobject.yml", YmlSampleObject().javaClass))
          .isA<YmlSampleObject>()
          .and {
            get { children }.isA<List<YmlSampleObject.YmlChildObject>>().hasSize(2)
            get { parentAttr1 }.isEqualTo("PA1")
            get { parentAttr2 }.isEqualTo("PA2")
            get { children?.get(0)?.childAttr1 }.isEqualTo("CA11")
            get { children?.get(0)?.childAttr2 }.isEqualTo("CA12")
            get { children?.get(1)?.childAttr1 }.isEqualTo("CA21")
            get { children?.get(1)?.childAttr2 }.isEqualTo("CA22")
          }
      }
    }
  }

  private inner class Fixture {
    val subject = JacksonYamlResourceLoader(this.javaClass)
  }

  class YmlSampleObject() {
    var parentAttr1: String? = null
    var parentAttr2: String? = null

    var children: List<YmlChildObject>? = null

    class YmlChildObject() {
      var childAttr1: String? = null
      var childAttr2: String? = null
    }
  }
}
