/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.cats.cache.AgentIntrospection
import com.netflix.spinnaker.cats.cache.CacheIntrospectionStore
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cache")
class CacheController {

  @Autowired
  List<OnDemandCacheUpdater> onDemandCacheUpdaters

  @RequestMapping(method = RequestMethod.POST, value = "/{cloudProvider}/{type}")
  ResponseEntity handleOnDemand(@PathVariable String cloudProvider,
                                @PathVariable String type,
                                @RequestBody Map<String, ? extends Object> data) {
    OnDemandAgent.OnDemandType onDemandType = getOnDemandType(type);

    def onDemandCacheResult = onDemandCacheUpdaters.find {
      it.handles(onDemandType, cloudProvider)
    }?.handle(onDemandType, cloudProvider, data)

    def cacheStatus = onDemandCacheResult?.status
    def httpStatus = (cacheStatus == OnDemandCacheUpdater.OnDemandCacheStatus.PENDING) ? HttpStatus.ACCEPTED : HttpStatus.OK

    return new ResponseEntity(
      [
        cachedIdentifiersByType: onDemandCacheResult?.cachedIdentifiersByType ?: [:]
      ],
      httpStatus
    )
  }


  @RequestMapping(method = RequestMethod.GET, value = "/introspection")
  Collection <AgentIntrospection> getAgentIntrospections() {
    return CacheIntrospectionStore.getStore().listAgentIntrospections()
        // sort by descending start time, so newest executions are first
        .toSorted { a, b -> b.getLastExecutionStartMs() <=> a.getLastExecutionStartMs() }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}/{type}")
  Collection<Map> pendingOnDemands(@PathVariable String cloudProvider,
                                   @PathVariable String type,
                                   @RequestParam(value = "id", required = false) String id) {
    OnDemandAgent.OnDemandType onDemandType = getOnDemandType(type)
    onDemandCacheUpdaters.findAll {
      it.handles(onDemandType, cloudProvider)
    }?.collect {
      if (id) {
        def pendingOnDemandRequest = it.pendingOnDemandRequest(onDemandType, cloudProvider, id)
        return pendingOnDemandRequest ? [ pendingOnDemandRequest ] : []
      }
      return it.pendingOnDemandRequests(onDemandType, cloudProvider)
    }.flatten()
  }

  static OnDemandAgent.OnDemandType getOnDemandType(String type) {
    try {
      return OnDemandAgent.OnDemandType.fromString(type)
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(e.message)
    }
  }
}
