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

package com.netflix.spinnaker.echo.config

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kafka configuration
 */
@Configuration
@SuppressWarnings('GStringExpressionWithinString')
class KafkaProducerConfig {

    @Bean
    KafkaProducer producer(
        @Value('${kafka.client.id}') String clientId,
        @Value('${kafka.bootstrap.servers}') String bootStrapServers,
        @Value('${kafka.compression.type}') String compression,
        @Value('${kafka.acks}') String acks
    ) {
        Properties props = new Properties()
        props.put('client.id', clientId)
        props.put('bootstrap.servers', bootStrapServers)
        props.put('acks', acks)
        props.put('compression.type', compression)
        props.put('block.on.buffer.full', Boolean.FALSE)
        KafkaProducer producer = new KafkaProducer<>(props, new ByteArraySerializer(), new ByteArraySerializer())
        producer
    }

}
