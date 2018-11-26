/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import javax.servlet.http.HttpServletResponse

internal object ReducedResponseSpec : Spek({

  describe("applying a reduced response to a HttpServletResponse") {
    given("a reduced response") {
      val reducedResponse = ReducedResponse(
        status = 200,
        headers = mapOf(),
        contentType = "application/json",
        characterEncoding = "UTF-8",
        body = "Hello world!",
        isError = false
      )

      val byteArrayOutputStream = ByteArrayOutputStream()
      val printWriter = PrintWriter(byteArrayOutputStream)

      val servletResponse = mock<HttpServletResponse>() {
        on { writer } doReturn printWriter
      }

      it("applies values to servlet response") {
        reducedResponse.applyTo(servletResponse).flush()

        verify(servletResponse).status = eq(200)
        verify(servletResponse).contentType = eq("application/json")
        verify(servletResponse).characterEncoding = eq("UTF-8")
        verify(servletResponse).setContentLength(eq(reducedResponse.body!!.length ))

        expectThat(byteArrayOutputStream) {
          get { byteArrayOutputStream.toString() }.isEqualTo("Hello world!")
        }
      }
    }
  }
})
