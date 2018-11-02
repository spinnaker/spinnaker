/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/assets"])
class AssetController() {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    consumes = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE],
    produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE]
  )
  fun create(@RequestBody resource: Asset): Asset {
    log.info("Creating: $resource")
    return resource
  }
}
