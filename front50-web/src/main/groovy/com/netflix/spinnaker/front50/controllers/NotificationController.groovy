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

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.Notification
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for presets
 */
@Slf4j
@RestController
@RequestMapping('notifications')
class NotificationController {

    @Autowired
    NotificationDAO notificationDAO

    @Autowired
    MessageSource messageSource

    @RequestMapping(value = '', method = RequestMethod.GET)
    List<Notification> list() {
        notificationDAO.all()
    }

    @RequestMapping(value = 'global', method = RequestMethod.GET)
    Notification getGlobal() {
        notificationDAO.getGlobal()
    }

    @RequestMapping(value = 'global', method = RequestMethod.POST)
    void saveGlobal(@RequestBody Notification notification) {
        notificationDAO.saveGlobal(notification)
    }

    @PostAuthorize("hasPermission(#name, 'APPLICATION', 'READ')")
    @RequestMapping(value = '{type}/{name}', method = RequestMethod.GET)
    Notification listByApplication(@PathVariable(value = 'type') String type, @PathVariable(value = 'name') String name) {
        HierarchicalLevel level = getLevel(type)
        def notification = notificationDAO.get(level, name)

        if (level == HierarchicalLevel.APPLICATION) {
            def global = getGlobal()
            NotificationDAO.NOTIFICATION_FORMATS.each {
                if (global."$it") {
                    if (!notification."${it}") {
                        notification."${it}" = []
                    }
                    notification."$it".addAll(global."$it")
                }
            }
        }

        return notification
    }

    @RequestMapping(value = 'batchUpdate', method = RequestMethod.POST)
    void batchUpdate(@RequestBody List<Notification> notifications) {
        notifications.each { it ->
            try {
                boolean isGlobal = false

                if( it.hipchat )
                    isGlobal = it.hipchat.first().level == 'global'

                if (isGlobal) {
                    notificationDAO.saveGlobal(it)
                } else {
                    save('application', it.application, it)
                }
                log.info("inserted ${it}")
            } catch (e) {
                log.error("could not insert ${it}", e)
            }
        }
    }

    @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#name, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = '{type}/{name}', method = RequestMethod.POST)
    void save(
            @PathVariable(value = 'type') String type,
            @PathVariable(value = 'name') String name,
            @RequestBody Notification notification) {
        HierarchicalLevel level = getLevel(type)
        if (name) {
            notificationDAO.save(level, name, notification)
        }
    }

    @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#name, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = '{type}/{name}', method = RequestMethod.DELETE)
    void delete(@PathVariable(value = 'type') String type, @PathVariable(value = 'name') String name) {
        HierarchicalLevel level = getLevel(type)
        notificationDAO.delete(level, name)
    }

    private static HierarchicalLevel getLevel(String type) {
        HierarchicalLevel result = HierarchicalLevel.fromString(type)
        if (!result) {
            throw new NotFoundException("No hierarchical level matches '${type}'")
        }
        result
    }
}
