/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.veto.Veto
import com.netflix.spinnaker.keel.veto.exceptions.MalformedMessageException
import com.netflix.spinnaker.keel.veto.exceptions.VetoNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/vetos"])
class VetoController(
  val vetos: List<Veto>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @GetMapping(
    produces = [APPLICATION_JSON_VALUE]
  )
  fun getActiveVetos(): List<String> = vetos.map { it.name() }

  @GetMapping(
    path = ["/{name}/message-format"],
    produces = [APPLICATION_JSON_VALUE]
  )
  fun getVetoMessageFormat(@PathVariable name: String): Map<String, Any> {
    val veto = vetos.find { it.name().equals(name, true) } ?: throw VetoNotFoundException(name)
    return mapOf(
      "messageURL" to "POST /vetos/{name}",
      "name" to name,
      "messageBodyFormat" to veto.messageFormat()
    )
  }

  @GetMapping(
    path = ["/{name}/rejections"],
    produces = [APPLICATION_JSON_VALUE]
  )
  fun getVetoRejections(@PathVariable name: String): List<String> {
    val veto = vetos.find { it.name().equals(name, true) } ?: throw VetoNotFoundException(name)
    return veto.currentRejections()
  }

  @GetMapping(
    path = ["/application/{application}/rejections"],
    produces = [APPLICATION_JSON_VALUE]
  )
  fun getVetoRejectionsByApp(@PathVariable application: String): List<String> =
    vetos.map { veto ->
      veto.currentRejectionsByApp(application)
    }.flatten()

  @PostMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE]
  )
  fun passMessage(@PathVariable name: String, @RequestBody message: Map<String, Any>) {
    val veto = vetos.find { it.name().equals(name, true) } ?: throw VetoNotFoundException(name)
    veto.passMessage(message)
  }

  @ExceptionHandler(VetoNotFoundException::class)
  @ResponseStatus(NOT_FOUND)
  fun onNotFound(e: VetoNotFoundException): Map<String, Any?> {
    log.error(e.message)
    return mapOf("message" to e.message)
  }

  @ExceptionHandler(MalformedMessageException::class)
  @ResponseStatus(BAD_REQUEST)
  fun onMalformedMessage(e: MalformedMessageException): Map<String, Any?> {
    log.error(e.message)
    return mapOf("message" to e.message)
  }
}
