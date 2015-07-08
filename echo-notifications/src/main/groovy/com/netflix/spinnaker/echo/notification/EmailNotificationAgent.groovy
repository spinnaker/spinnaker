/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

import com.netflix.spinnaker.echo.email.EmailNotificationService
import groovy.util.logging.Slf4j
import org.apache.velocity.app.VelocityEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.ui.velocity.VelocityEngineUtils

@Slf4j
@Service
class EmailNotificationAgent extends AbstractEventNotificationAgent {

    @Autowired
    EmailNotificationService mailService

    @Autowired
    VelocityEngine engine

    @Override
    void sendNotifications(Map event, Map config, String status) {
        String application = event.details.application
        log.info("${lastCheck}: Event: ${application} ${config.type} ${status} ${event.content?.executionId}")
        String[] addresses = getEmailReceipients(application, config.type, status)

        String buildInfo = ''

        if(config.type == 'pipeline'){
            if(event.content?.execution?.trigger?.buildInfo?.url){
                buildInfo = """build #${event.content.execution.trigger.buildInfo.number as Integer} """
            }
        }

        if (addresses.length > 0) {
            log.info("${lastCheck}: Send Email: ${addresses} for ${application} ${config.type} ${status} ${event.content?.executionId}")
            sendMessage(
                    addresses,
                    event,
                    """[Spinnaker] ${config.type} for ${event.content?.execution?.name ?: event.content?.execution?.description} ${buildInfo}${status == 'starting' ? 'is' : 'has'} ${status == 'complete' ? 'completed successfully' : status} for application ${application}""",
                    config.type,
                    status,
                    config.link
            )
        }
    }

    private String[] getEmailReceipients(String application, String type, String status) {
        List addresses = []
        mayoService.getNotificationPreferences(application)?.email?.each { emailPreference ->
            if (emailPreference.when?.contains("$type.$status".toString())) {
                addresses << emailPreference.address
            }
        }
        addresses.toArray()
    }

    private void sendMessage(String[] email, Map event, String title, String type, String status, String link) {
        mailService.send(
                email,
                title,
                VelocityEngineUtils.mergeTemplateIntoString(
                        engine,
                        'email.vm',
                        "UTF-8",
                        [
                                event      : prettyPrint(toJson(event.content)),
                                url        : spinnakerUrl,
                                application: event.details?.application,
                                executionId: event.content?.execution?.id,
                                type       : type,
                                status     : status,
                                link       : link,
                                name       : event.content?.execution?.name ?: event.content?.execution?.description
                        ]
                )
        )
    }

}
