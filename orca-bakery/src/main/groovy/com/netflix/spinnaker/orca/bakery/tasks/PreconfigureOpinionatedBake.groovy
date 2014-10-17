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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.Stage

class PreconfigureOpinionatedBake implements Task {
  @Override
  TaskResult execute(Stage stage) {
    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "bake.user"     : "orca",
      "bake.baseOs"   : "ubuntu",
      "bake.baseLabel": "release",
      "bake.package": stage.context.package,
      "bake.region" : stage.context.region
    ])
  }
}
