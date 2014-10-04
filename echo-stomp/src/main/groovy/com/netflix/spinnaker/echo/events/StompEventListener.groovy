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

package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.model.Event
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Event listener for stomp
 */
@Component
class StompEventListener implements EchoEventListener {

    @Autowired
    SimpMessagingTemplate template

    @Override
    void processEvent(Event event) {

        if (event.details.application) {
            template.convertAndSend(
                '/topic/application/' + event.details.application,
                event
            )
        }

        template.convertAndSend(
            '/topic/source/' + event.details.source,
            event
        )

        template.convertAndSend(
            '/topic/type/' + event.details.type,
            event
        )

        template.convertAndSend(
            '/topic/events',
            event
        )

    }

}
