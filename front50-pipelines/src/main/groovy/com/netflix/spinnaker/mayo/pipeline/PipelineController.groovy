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

package com.netflix.spinnaker.mayo.pipeline

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for presets
 */
@RestController
@RequestMapping('pipelines')
class PipelineController {

    @Autowired
    PipelineRepository pipelineRepository

    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Map> list() {
        pipelineRepository.list()
    }

    @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
    List<Map> listByApplication(@PathVariable(value = 'application') String application) {
        pipelineRepository.getPipelinesByApplication(application)
    }

    @RequestMapping(value = '', method = RequestMethod.POST)
    void save(@RequestBody Map pipeline) {
        pipelineRepository.save(pipeline)
    }

    @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
    void batchUpdate(@RequestBody List<Map> pipelines) {
        pipelineRepository.batchUpdate(pipelines)
    }

    @RequestMapping(value = '{application}/{pipeline:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String application, @PathVariable String pipeline) {
        pipelineRepository.delete(application, pipeline)
    }

    @RequestMapping(value = 'deleteById/{id:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String id) {
        pipelineRepository.deleteById(id)
    }

    @RequestMapping(value = 'move', method = RequestMethod.POST)
    void rename(@RequestBody RenameCommand command) {
        pipelineRepository.rename(command.application, command.from, command.to)
    }

    static class RenameCommand {
        String application
        String from
        String to
    }

}
