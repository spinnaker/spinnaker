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
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import redis.clients.jedis.JedisCommands

class MultiThreadedJedisBatchConfigurer extends JedisBatchConfigurer {

  @Autowired
  MultiThreadedJedisBatchConfigurer(JedisCommands jedis) {
    super(jedis)
  }

  @Override
  public JobLauncher getJobLauncher() {
    def launcher = new SimpleJobLauncher()
    launcher.jobRepository = jobRepository
    launcher.taskExecutor = taskExecutor
    launcher.afterPropertiesSet()
    launcher
  }

  private static TaskExecutor getTaskExecutor() {
    def executor = new ThreadPoolTaskExecutor()
    executor.maxPoolSize = 250
    executor.corePoolSize = 50
    executor.afterPropertiesSet()
    executor
  }
}
