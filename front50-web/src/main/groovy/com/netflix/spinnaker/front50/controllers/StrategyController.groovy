/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for presets
 */
@RestController
@RequestMapping('strategies')
class StrategyController {

    @Autowired
    PipelineStrategyDAO pipelineStrategyDAO

    @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Pipeline> list() {
        pipelineStrategyDAO.all()
    }

    @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
    @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
    List<Pipeline> listByApplication(@PathVariable(value = 'application') String application) {
        pipelineStrategyDAO.getPipelinesByApplication(application)
    }

    @PostAuthorize("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = '{id:.+}/history', method = RequestMethod.GET)
    Collection<Pipeline> getHistory(@PathVariable String id,
                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return pipelineStrategyDAO.getPipelineHistory(id, limit)
    }

    @PreAuthorize("hasPermission(#strategy.application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = '', method = RequestMethod.POST)
    void save(@RequestBody Pipeline strategy) {
        if (!strategy.id) {
            checkForDuplicatePipeline(strategy.getApplication(), strategy.getName())
            // ensure that cron triggers are assigned a unique identifier for new strategies
            def triggers = (strategy.triggers ?: []) as List<Map>
            triggers.findAll { it.type == "cron" }.each { Map trigger ->
                trigger.id = UUID.randomUUID().toString()
            }
        }

        pipelineStrategyDAO.create(strategy.getId(), strategy)
    }

    // TODO(ttomsu): Bulk authorize capability needed.
    // @PreFilter("hasPermission(#filterTarget.application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
    void batchUpdate(@RequestBody List<Pipeline> strategies) {
        pipelineStrategyDAO.bulkImport(strategies)
    }

    @PreAuthorize("hasPermission(#application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = '{application}/{strategy:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String application, @PathVariable String strategy) {
        pipelineStrategyDAO.delete(
            pipelineStrategyDAO.getPipelineId(application, strategy)
        )
    }

    // TODO(ttomsu): How to secure this endpoint?
    @RequestMapping(value = 'deleteById/{id:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String id) {
        pipelineStrategyDAO.delete(id)
    }

    @PreAuthorize("hasPermission(#command.application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = 'move', method = RequestMethod.POST)
    void rename(@RequestBody RenameCommand command) {
        checkForDuplicatePipeline(command.application, command.to)
        def pipelineId = pipelineStrategyDAO.getPipelineId(command.application, command.from)
        def pipeline = pipelineStrategyDAO.findById(pipelineId)
        pipeline.setName(command.to)

        pipelineStrategyDAO.update(pipelineId, pipeline)
    }

    private void checkForDuplicatePipeline(String application, String name) {
        if (pipelineStrategyDAO.getPipelinesByApplication(application).any { it.getName() == name}) {
            throw new DuplicateStrategyException()
        }
    }

    @ExceptionHandler(DuplicateStrategyException)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map handleDuplicateStrategyNameException() {
        return [error: "A strategy with that name already exists in that application", status: HttpStatus.BAD_REQUEST]
    }

    @ExceptionHandler(AccessDeniedException)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map handleAccessDeniedException(AccessDeniedException ade) {
        return [error: "Access is denied", status: HttpStatus.FORBIDDEN.value()]
    }

    static class DuplicateStrategyException extends Exception {}

    static class RenameCommand {
        String application
        String from
        String to
    }
}
