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

import com.netflix.spinnaker.front50.pipeline.StrategyRepository
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
@RequestMapping('strategies')
class StrategyController {

    @Autowired
    StrategyRepository strategyRepository

    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Map> list() {
        strategyRepository.list()
    }

    @RequestMapping(value = '{application:.+}', method = RequestMethod.GET)
    List<Map> listByApplication(@PathVariable(value = 'application') String application) {
        strategyRepository.getPipelinesByApplication(application)
    }

    @RequestMapping(value = '', method = RequestMethod.POST)
    void save(@RequestBody Map strategy) {
        if (!strategy.id) {
            // ensure that cron triggers are assigned a unique identifier for new strategies
            def triggers = (strategy.triggers ?: []) as List<Map>
            triggers.findAll { it.type == "cron" }.each { Map trigger ->
                trigger.id = UUID.randomUUID().toString()
            }
        }

        strategyRepository.save(strategy)
    }

    @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
    void batchUpdate(@RequestBody List<Map> strategies) {
        strategyRepository.batchUpdate(strategies)
    }

    @RequestMapping(value = '{application}/{strategy:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String application, @PathVariable String strategy) {
        strategyRepository.delete(application, strategy)
    }

    @RequestMapping(value = 'deleteById/{id:.+}', method = RequestMethod.DELETE)
    void delete(@PathVariable String id) {
        strategyRepository.deleteById(id)
    }

    @RequestMapping(value = 'move', method = RequestMethod.POST)
    void rename(@RequestBody RenameCommand command) {
        strategyRepository.rename(command.application, command.from, command.to)
    }

    static class RenameCommand {
        String application
        String from
        String to
    }

}
