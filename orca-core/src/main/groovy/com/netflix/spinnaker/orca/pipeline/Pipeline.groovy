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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.PipelineStatus
import static com.netflix.spinnaker.orca.PipelineStatus.*

@CompileStatic
class Pipeline {

  final String id
  final ImmutableList<Stage> stages

  Pipeline(String id, List<Stage> stages) {
    this.id = id
    this.stages = ImmutableList.copyOf(stages)
  }

  Pipeline(String id, Stage... stages) {
    this(id, stages.toList())
  }

  PipelineStatus getStatus() {
    def status = stages.status.reverse().find {
      it != NOT_STARTED
    }

    if (!status) {
      NOT_STARTED
    } else if (status == SUCCEEDED && stages.last().status != SUCCEEDED) {
      RUNNING
    } else {
      status
    }
  }
}
