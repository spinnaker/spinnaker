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

package com.netflix.spinnaker.front50.notifications

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
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
@RequestMapping('notifications')
class NotificationController {

    @Autowired
    NotificationRepository notificationRepository

    @Autowired
    MessageSource messageSource

    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Map> list() {
        notificationRepository.list()
    }

    @RequestMapping(value = 'global', method = RequestMethod.GET)
    Map getGlobal() {
        notificationRepository.getGlobal()
    }

    @RequestMapping(value = 'global', method = RequestMethod.POST)
    void saveGlobal(@RequestBody Map notification) {
        notificationRepository.saveGlobal(notification)
    }

    @RequestMapping(value = '{type}/{name}', method = RequestMethod.GET)
    Map listByApplication(@PathVariable(value = 'type') String type, @PathVariable(value = 'name') String name) {
        HierarchicalLevel level = getLevel(type)
        notificationRepository.get(level, name)
    }

    @RequestMapping(value = '{type}/{name}', method = RequestMethod.POST)
    void save(@PathVariable(value = 'type') String type, @PathVariable(value = 'name') String name, @RequestBody Map notification) {
        HierarchicalLevel level = getLevel(type)
        if (name) {
            notificationRepository.save(level, name, notification)
        }
    }

    @RequestMapping(value = '{type}/{name}', method = RequestMethod.DELETE)
    void delete(@PathVariable(value = 'type') String type, @PathVariable(value = 'name') String name) {
        HierarchicalLevel level = getLevel(type)
        notificationRepository.delete(level, name)
    }

    private static HierarchicalLevel getLevel(String type) {
        HierarchicalLevel result = HierarchicalLevel.fromString(type)
        if (!result) {
            throw new HierarchicalLevelNotFoundException(type: type)
        }
        result
    }

    static class HierarchicalLevelNotFoundException extends RuntimeException {
        String type
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map hierarchicalLevelNotFoundExceptionHandler(HierarchicalLevelNotFoundException ex) {
        def message = messageSource.getMessage("level.not.found", [ex.type] as String[], "No hierarchical level matches '${ex.type}'", LocaleContextHolder.locale)
        [error: "level.not.found", message: message, status: HttpStatus.NOT_FOUND]
    }
}
