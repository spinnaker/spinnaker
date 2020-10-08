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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.api.test.OrcaFixture
import com.netflix.spinnaker.orca.api.test.orcaFixture
import com.netflix.spinnaker.orca.clouddriver.KatoRestService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import strikt.api.expect
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class KubernetesPreconfiguredJobSpec : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    orcaFixture {
      Fixture()
    }

    context("kubernetes preconfigured job") {
      test("preconfigured jobs endpoint returns job definition") {
        val resp = subject.get("/jobs/preconfigured")
          .andReturn().response

        expect {
          that(resp.status).isEqualTo(200)
          that(resp.contentAsString)
            .contains("\"label\":\"Test Preconfigured Job\"")
            .contains("\"description\":\"Preconfigured job for testing\"")
            .contains("\"type\":\"testPreconfiguredJob\"")
        }
      }
      test("can execute a pipeline that contains a stage type provided by the preconfigured job definition") {

        val pipeline =
          """
          {
          "stages":[{
            "alias":"preconfiguredJob",
            "name":"Test Preconfigured Job",
            "parameters":{"Counter Limit":"20"},
            "refId":"1",
            "requisiteStageRefIds":[],
            "type":"testPreconfiguredJob"
          }]
          }
          """.trimIndent()

        every { katoRestService.requestOperations(any(), any(), any()) } returns TaskId("1")

        val resp = subject.post("/orchestrate") {
          contentType = MediaType.APPLICATION_JSON
          content = pipeline
        }.andReturn().response

        expect {
          that(resp.status).isEqualTo(200)
          that(resp.contentAsString)
            .contains("\"ref\":\"/pipelines")
        }

        verify(timeout = 1000) { katoRestService.requestOperations(any(), "kubernetes", match { it.toString().contains("alias=preconfiguredJob") }) }
      }
    }
  }

  @AutoConfigureMockMvc
  @TestPropertySource(properties = ["spring.config.location=classpath:orca-test-preconfigured-job.yml"])
  private inner class Fixture : OrcaFixture() {

    @Autowired
    lateinit var subject: MockMvc

    @MockkBean
    lateinit var katoRestService: KatoRestService
  }
}
