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

import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import spock.lang.AutoCleanup

class JedisJobExecutionDaoSpec extends JobExecutionDaoTck {

  def pool = new JedisPool(new JedisPoolConfig(), "localhost")
  @AutoCleanup def jedis = pool.resource

  @Override
  JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao) {
    new JedisJobExecutionDao(jedis, jobInstanceDao)
  }

  @Override
  JobInstanceDao createJobInstanceDao() {
    new JedisJobInstanceDao(jedis)
  }

  def cleanup() {
    jedis.flushDB()
  }
}
