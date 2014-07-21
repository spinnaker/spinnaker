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

import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.MapExecutionContextDao
import org.springframework.batch.core.repository.dao.MapJobExecutionDao
import org.springframework.batch.core.repository.dao.MapJobInstanceDao
import org.springframework.batch.core.repository.dao.MapStepExecutionDao
import org.springframework.batch.core.repository.dao.StepExecutionDao

class MapExecutionContextDaoSpec extends ExecutionContextDaoTck {

  @Override
  JobInstanceDao createJobInstanceDao() {
    new MapJobInstanceDao()
  }

  @Override
  JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao) {
    new MapJobExecutionDao()
  }

  @Override
  StepExecutionDao createStepExecutionDao() {
    new MapStepExecutionDao()
  }

  @Override
  ExecutionContextDao createExecutionContextDao() {
    new MapExecutionContextDao()
  }
}
