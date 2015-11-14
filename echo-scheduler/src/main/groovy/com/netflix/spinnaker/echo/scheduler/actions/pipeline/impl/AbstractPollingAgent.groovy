/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

public abstract class AbstractPollingAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPollingAgent.class)

    private final ScheduledExecutorService executorService

    protected AbstractPollingAgent() {
        this.executorService = Executors.newSingleThreadScheduledExecutor()
    }

    @PostConstruct
    public void init() {
        this.executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    execute()
                } catch (Throwable e) {
                    LOGGER.error(String.format("Exception occurred in %s polling agent", getName()), e)
                }
            }
        }, 60000L, getIntervalMs(), TimeUnit.MILLISECONDS)
        LOGGER.info("Started the {}...", getName())
    }

    public abstract String getName()

    public abstract long getIntervalMs()

    public abstract void execute()

    public void shutdown() {
        executorService.shutdownNow()
    }
}
