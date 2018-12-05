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
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.events.AssetEvent
import com.netflix.spinnaker.keel.events.AssetEventType.CREATE
import com.netflix.spinnaker.keel.events.AssetEventType.DELETE
import com.netflix.spinnaker.keel.events.AssetEventType.UPDATE
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.NoSuchAssetException
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/assets"])
class AssetController(
  private val publisher: ApplicationEventPublisher,
  private val assetRepository: AssetRepository
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    consumes = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE],
    produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE]
  )
  fun create(@RequestBody resource: Asset<*>): Asset<*> {
    log.info("Creating: $resource")
    publisher.publishEvent(AssetEvent(CREATE, resource))
    return resource // TODO: after it's been thru k8s
  }

  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE]
  )
  fun get(@PathVariable("name") name: AssetName): Asset<Any> {
    log.info("Getting: $name")
    return assetRepository.get(name)
  }

  @PutMapping(
    path = ["/{name}"],
    produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE]
  )
  fun update(@PathVariable("name") name: AssetName, @RequestBody resource: Asset<*>): Asset<*> {
    log.info("Updating: $resource")
    publisher.publishEvent(AssetEvent(UPDATE, resource))
    return resource // TODO: after it's been thru k8s
  }

  @DeleteMapping(
    path = ["/{name}"],
    produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE]
  )
  fun delete(@PathVariable("name") name: AssetName): Asset<*> {
    log.info("Deleting: $name")
    val resource = assetRepository.get<Any>(name)
    publisher.publishEvent(AssetEvent(DELETE, resource))
    return resource // TODO: after it's been thru k8s
  }

  @ExceptionHandler(NoSuchAssetException::class)
  @ResponseStatus(NOT_FOUND)
  fun onNotFound(e: NoSuchAssetException) {
    log.error(e.message)
  }
}
