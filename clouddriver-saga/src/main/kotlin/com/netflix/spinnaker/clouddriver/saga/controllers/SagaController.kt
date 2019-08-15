/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.saga.controllers

import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository.ListCriteria
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/saga")
@RestController
class SagaController(
  private val sagaRepository: SagaRepository
) {

  @GetMapping
  fun listAll(
    @RequestParam("running", required = false) running: Boolean? = null,
    @RequestParam("names", required = false) names: List<String>? = null
  ): List<Saga> {
    return sagaRepository.list(ListCriteria(
      running = running,
      names = names
    ))
  }

  @GetMapping("/{name}")
  fun listByName(
    @PathVariable("name") name: String,
    @RequestParam("running", required = false) running: Boolean? = null
  ): List<Saga> {
    return sagaRepository.list(ListCriteria(
      running = running,
      names = listOf(name)
    ))
  }

  @GetMapping("/{name}/{id}")
  fun get(@PathVariable("name") name: String, @PathVariable("id") id: String): Saga {
    return sagaRepository.get(name, id)
      ?: throw NotFoundException("Saga not found (name: $name, id: $id)")
  }
}
