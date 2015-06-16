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

package com.netflix.spinnaker.orca.web.config

import com.netflix.redbatch.config.JedisBatchConfigurer
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.ValueFunction
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.TaskExecutor
import redis.clients.jedis.JedisCommands

class MultiThreadedJedisBatchConfigurer extends JedisBatchConfigurer {

  private final TaskExecutor taskExecutor

  @Autowired
  MultiThreadedJedisBatchConfigurer(JedisCommands jedis, TaskExecutor taskExecutor) {
    super(jedis)
    this.taskExecutor = taskExecutor
  }

  @Override
  protected JobLauncher createJobLauncher() {
    def launcher = new SimpleJobLauncher()
    launcher.jobRepository = jobRepository
    launcher.taskExecutor = taskExecutor
    launcher.afterPropertiesSet()
    launcher
  }
}
