/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.mayo.config

import com.netflix.spinnaker.mayo.pipeline.PipelineRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Component
public class PipelineRepositoryHealthIndicator implements HealthIndicator {

    private final AtomicReference<Health> lastHealth = new AtomicReference<>(null)

    @Autowired
    PipelineRepository pipelineRepository

    @Override
    public Health health() {
        if (!lastHealth.get()) {
            return new Health.Builder().withDetail("PipelineRepository", "Cassandra").down().build()
        }
        return lastHealth.get()
    }

    @Scheduled(fixedDelay = 15000L)
    void pollForHealth() {
        def healthBuilder = new Health.Builder().up()
        try {
            pipelineRepository.save(
                ["name": "healthCheck", "stages": [["type": "wait", "name": "Wait", "waitTime": 10]], "triggers": [], "application": "spindemo", "index": 0, "id": "58b700a0-ed12-11e4-a8b3-f5bd0633341b"]
            )
            assert ("58b700a0-ed12-11e4-a8b3-f5bd0633341b" == pipelineRepository.get('spindemo', 'healthCheck'))
        } catch (e) {
            healthBuilder.withException("PipelineRepository", "Unhealthy: `${e.message}`" as String)
        }
        lastHealth.set(healthBuilder.build())
    }
}
