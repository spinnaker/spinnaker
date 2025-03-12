/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.orca.api.test.OrcaFixture
import com.netflix.spinnaker.orca.api.test.orcaFixture
import com.sun.net.httpserver.HttpServer
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.net.InetSocketAddress
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import strikt.api.expect
import strikt.assertions.isNotNull

class PipelineTemplateServiceSpec : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    orcaFixture {
      Fixture()
    }

    context("pipeline template controller") {
      test("does not leak information about the endpoints it searches if it can't deserialize the response into a template") {
        val resp = subject.get("/pipelineTemplate") {
          param("source", "http://localhost:${server.address.port}")
        }.andReturn().response

        expect {
          that(resp.errorMessage)
            .isNotNull()
            .assert("should not include the content of the private endpoint") {
              when {
                it.contains("Private message - don't read me!") -> fail()
                else -> pass()
              }
            }
        }
      }
    }
  }

  @AutoConfigureMockMvc
  private inner class Fixture : OrcaFixture() {

    @Autowired
    lateinit var subject: MockMvc

    var server: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
      createContext("/") {
        it.responseHeaders.set("Content-Type", "text/plain")
        it.sendResponseHeaders(200, 0)
        it.responseBody.write("Private message - don't read me!".toByteArray())
        it.responseBody.close()
      }
      start()
    }
  }
}
