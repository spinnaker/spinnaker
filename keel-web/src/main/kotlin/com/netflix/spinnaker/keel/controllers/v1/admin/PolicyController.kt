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
package com.netflix.spinnaker.keel.controllers.v1.admin

import com.netflix.spinnaker.keel.Policy
import com.netflix.spinnaker.keel.PolicyRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/admin/policy")
class PolicyController
@Autowired constructor(
  private val policyRepository: PolicyRepository
) {

  @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
  fun list() = policyRepository.findAll()

  @RequestMapping(value = "", method = arrayOf(RequestMethod.PUT))
  fun upsert(@RequestBody req: Policy) = policyRepository.upsert(req)

  @RequestMapping(value = "/{id}", method = arrayOf(RequestMethod.DELETE))
  fun delete(@PathVariable("id") id: String) {
    policyRepository.delete(id)
  }
}
