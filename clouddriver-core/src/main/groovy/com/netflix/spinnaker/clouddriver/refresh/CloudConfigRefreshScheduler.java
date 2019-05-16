/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.refresh;

import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.cloud.context.refresh.ContextRefresher;

/**
 * Refresh the Spring Cloud Config context on a schedule. The configured interval should
 * approximately match the cache refresh interval.
 */
public class CloudConfigRefreshScheduler implements Runnable {
  private final ContextRefresher contextRefresher;

  public CloudConfigRefreshScheduler(ContextRefresher contextRefresher, long interval) {
    this.contextRefresher = contextRefresher;

    Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory(CloudConfigRefreshScheduler.class.getSimpleName()))
        .scheduleAtFixedRate(this, interval, interval, TimeUnit.SECONDS);
  }

  @Override
  public void run() {
    contextRefresher.refresh();
  }
}
