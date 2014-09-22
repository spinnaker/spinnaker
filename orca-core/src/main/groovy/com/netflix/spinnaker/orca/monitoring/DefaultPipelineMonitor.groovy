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

package com.netflix.spinnaker.orca.monitoring

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.TaskResult.Status

@CompileStatic
class DefaultPipelineMonitor implements PipelineMonitor {
  @Override
  void beginStage(String stageName) {

  }

  @Override
  void endStage(String stageName) {

  }

  @Override
  void beginTask() {

  }

  @Override
  void endTask(Status status) {

  }
}
