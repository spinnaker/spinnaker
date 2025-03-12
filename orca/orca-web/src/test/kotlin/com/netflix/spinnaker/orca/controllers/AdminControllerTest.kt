/*
 * Copyright 2021 Armory, Inc.
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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.orca.api.test.OrcaFixture
import com.netflix.spinnaker.orca.api.test.orcaFixture
import com.netflix.spinnaker.q.discovery.DiscoveryActivator
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class AdminControllerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    orcaFixture {
      Fixture()
    }

    test("/admin/instance/enabled endpoint can disable queue Activator") {
      // wait up to 5 seconds for discoveryActivator to initialize
      for (i in 1..5) {
         if (discoveryActivator.enabled){
            println("discoveryActivator.enabled = true. Start")
            break
         }
         println("discoveryActivator.enabled = false. Sleep 1sec")
         Thread.sleep(1000L)
      }

      expectThat(discoveryActivator.enabled).isTrue()

      val response = mockMvc.post("/admin/instance/enabled") {
        contentType = MediaType.APPLICATION_JSON
        content = jacksonObjectMapper().writeValueAsString(mapOf("enabled" to false))
      }.andReturn().response

      expectThat(response.status).isEqualTo(200)

      expectThat(discoveryActivator.enabled).isFalse()
    }
  }

  @AutoConfigureMockMvc
  class Fixture : OrcaFixture() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var discoveryActivator: DiscoveryActivator
  }
}
