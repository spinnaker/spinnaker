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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Event listener for echo events
 */
@Component
@Slf4j
@SuppressWarnings('GStringExpressionWithinString')
class KafkaEventListener implements EchoEventListener {

    ObjectMapper mapper = new ObjectMapper()

    @Value('${kafka.prefix}')
    String prefix

    @Value('${kafka.hostname}')
    String hostname

    @Value('${kafka.app:spinnaker}')
    String app

    @Value('${kafka.rowid:events}')
    String rowid

    @Value('${kafka.topic:spinnaker_events}')
    String topic

    @Autowired(required=false)
    KafkaProducer producer

    @Override
    void processEvent(Event event) {
        if (KafkaProducer) {
            Map eventAsMap = mapper.convertValue(event, Map)
            eventAsMap."${prefix}_ts" = new Date().time
            eventAsMap."${prefix}_app" = app
            eventAsMap."${prefix}_hostname" = hostname
            eventAsMap."${prefix}_rowid" = UUID.randomUUID()
            try {
                ProducerRecord data = new ProducerRecord(topic, mapper.writeValueAsBytes(eventAsMap))
                producer.send(data).get()
            } catch (e) {
                log.error("Cannot send event", event, e)
            }
        }
    }

}
