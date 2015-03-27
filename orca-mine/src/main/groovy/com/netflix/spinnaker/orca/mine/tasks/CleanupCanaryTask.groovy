/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.tasks.DisableAsgTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import java.util.concurrent.TimeUnit

@Component
class CleanupCanaryTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = TimeUnit.SECONDS.toMillis(30)

  @Autowired MineService mineService

  @Autowired DisableAsgTask disableAsgTask

  @Override
  TaskResult execute(Stage stage) {
    //TODO(cfieber) - disable or destroy asgs
    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
  }

}
