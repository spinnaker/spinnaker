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
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for presets
 */
@RestController
@RequestMapping('pipelines')
class PipelineController {

    @Autowired
    PipelineDAO pipelineDAO

    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Pipeline> list() {
        pipelineDAO.all()
    }

    @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
    List<Pipeline> listByApplication(@PathVariable(value = 'application') String application) {
        pipelineDAO.getPipelinesByApplication(application)
    }

    @RequestMapping(value = '', method = RequestMethod.POST)
    void save(@RequestBody Pipeline pipeline) {
        if (!pipeline.id) {
            // ensure that cron triggers are assigned a unique identifier for new pipelines
            def triggers = (pipeline.triggers ?: []) as List<Map>
            triggers.findAll { it.type == "cron" }.each { Map trigger ->
                trigger.id = UUID.randomUUID().toString()
            }
            if (pipelineDAO.getPipelineId(pipeline.getApplication(), pipeline.getName())) {
                throw new DuplicatePipelineNameException()
            }
        }

        pipelineDAO.create(pipeline.id as String, pipeline)
    }

    @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
    void batchUpdate(@RequestBody List<Pipeline> pipelines) {
        pipelineDAO.bulkImport(pipelines)
    }

    @RequestMapping(value = '{application}/{pipeline:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String application, @PathVariable String pipeline) {
        pipelineDAO.delete(
            pipelineDAO.getPipelineId(application, pipeline)
        )
    }

    @RequestMapping(value = 'deleteById/{id:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String id) {
        pipelineDAO.delete(id)
    }

    @RequestMapping(value = 'move', method = RequestMethod.POST)
    void rename(@RequestBody RenameCommand command) {
        if (pipelineDAO.getPipelineId(command.application, command.to)) {
            throw new DuplicatePipelineNameException()
        }
        def pipelineId = pipelineDAO.getPipelineId(command.application, command.from)
        def pipeline = pipelineDAO.findById(pipelineId)
        pipeline.setName(command.to)

        pipelineDAO.update(pipelineId, pipeline)
    }

    static class RenameCommand {
        String application
        String from
        String to
    }

    @ExceptionHandler(DuplicatePipelineNameException)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map handleDuplicatePipelineNameException() {
        return [error: "A pipeline with that name already exists in that application", status: HttpStatus.BAD_REQUEST]
    }

    static class DuplicatePipelineNameException extends Exception {}
}
