/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.TagService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult

@RequestMapping("/applications")
@RestController
class ApplicationController {

  @Autowired
  ApplicationService applicationService

  @Autowired
  TagService tagService

  @RequestMapping(method = RequestMethod.GET)
  def get() {
    DeferredResult<List> q = new DeferredResult<>()
    applicationService.all.toList().subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  def show(@PathVariable("name") String name) {
    DeferredResult<Map> q = new DeferredResult<>()
    applicationService.get(name).doOnError({ Throwable t ->
      q.setErrorResult(t)
    }).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.GET)
  def getTasks(@PathVariable("name") String name) {
    DeferredResult<List> q = new DeferredResult<>()
    applicationService.getTasks(name).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }

  @RequestMapping(value = "/{name}/tasks", method = RequestMethod.POST)
  def create(@RequestBody Map map) {
    DeferredResult<Map> q = new DeferredResult<>()
    applicationService.create(map).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }

  // TODO get flapjack properly implemented
  @RequestMapping(value = "/{name}/tags", method = RequestMethod.GET)
  def getTags(@PathVariable("name") String name) {
    DeferredResult<List> q = new DeferredResult<>()
    tagService.getTags(name).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }
}
