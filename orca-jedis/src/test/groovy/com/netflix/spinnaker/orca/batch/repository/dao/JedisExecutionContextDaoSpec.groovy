/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.repository.dao

import com.netflix.spinnaker.kork.jedis.JedisConfig
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import redis.clients.jedis.Jedis
import spock.lang.AutoCleanup
import spock.lang.Shared

class JedisExecutionContextDaoSpec extends ExecutionContextDaoTck {

  @Shared @AutoCleanup("destroy") JedisConfig jedisConfig = new JedisConfig()
  @Shared Jedis jedis = jedisConfig.jedis(0, "127.0.0.1", System.env['redis.connection'] ?: "none")

  def cleanup() {
    jedis.flushDB()
  }

  @Override
  JobInstanceDao createJobInstanceDao() {
    new JedisJobInstanceDao(jedis)
  }

  @Override
  JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao) {
    new JedisJobExecutionDao(jedis, jobInstanceDao)
  }

  @Override
  StepExecutionDao createStepExecutionDao() {
    new JedisStepExecutionDao(jedis)
  }

  @Override
  ExecutionContextDao createExecutionContextDao() {
    new JedisExecutionContextDao(jedis)
  }
}
