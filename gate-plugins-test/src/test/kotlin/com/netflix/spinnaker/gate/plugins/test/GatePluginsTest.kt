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

package com.netflix.spinnaker.gate.plugins.test

import com.netflix.spinnaker.kork.plugins.tck.PluginsTck
import com.netflix.spinnaker.kork.plugins.tck.serviceFixture
import dev.minutest.rootContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import strikt.api.expect
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class GatePluginsTest : PluginsTck<GatePluginsFixture>() {

  fun tests() = rootContext<GatePluginsFixture> {
    context("a gate integration test environment and a gate plugin") {
      serviceFixture {
        GatePluginsFixture()
      }

      defaultPluginTests()

      test("Extension supports GET") {
        val response = mockMvc.perform(get("/extensions/test"))
          .andReturn()
          .response

        expect {
          that(response.status).isEqualTo(204)
        }
      }

      test("Extension supports PUT and echoes content") {
        val response = mockMvc.perform(put("/extensions/test/echo?parameter=foo"))
          .andReturn()
          .response

        expect {
          that(response.status).isEqualTo(200)
          that(response.contentAsString).contains("foo")
        }
      }

      test("Unsupported HTTP method results in a 404") {
        val response = mockMvc.perform(post("/extensions/test"))
          .andReturn()
          .response

        expect {
          that(response.status).isEqualTo(404)
        }
      }
    }
  }
}
