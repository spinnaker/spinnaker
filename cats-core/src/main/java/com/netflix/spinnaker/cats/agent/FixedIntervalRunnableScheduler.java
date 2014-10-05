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

package com.netflix.spinnaker.cats.agent;

import com.netflix.spinnaker.cats.thread.NamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FixedIntervalRunnableScheduler implements RunnableScheduler {

    private final long interval;
    private final TimeUnit unit;
    private final ScheduledExecutorService executorService;

    public FixedIntervalRunnableScheduler(String name, long interval, TimeUnit unit) {
        this.interval = interval;
        this.unit = unit;
        executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new NamedThreadFactory(name));
    }

    @Override
    public void schedule(Runnable command) {
        executorService.scheduleAtFixedRate(command, 0, interval, unit);
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }
}
