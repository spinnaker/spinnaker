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

package com.netflix.spinnaker.orca.igor.tasks

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.BuildArtifactFilter
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class MonitorJenkinsJobTask implements RetryableTask {

  long backoffPeriod = 10000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Autowired
  BuildArtifactFilter buildArtifactFilter

  private static Map<String, ExecutionStatus> statusMap = [
    'ABORTED' : ExecutionStatus.CANCELED,
    'FAILURE' : ExecutionStatus.TERMINAL,
    'SUCCESS' : ExecutionStatus.SUCCEEDED,
    'UNSTABLE': ExecutionStatus.TERMINAL
  ]

  @Override
  TaskResult execute(Stage stage) {
    String master = stage.context.master
    String job = stage.context.job

    if (!stage.context.buildNumber) {
      log.error("failed to get build number for job ${job} from master ${master}")
      return new TaskResult(ExecutionStatus.TERMINAL)
    }

    def buildNumber = (int) stage.context.buildNumber
    try {
      Map<String, Object> build = buildService.getBuild(buildNumber, master, job)
      Map outputs = [:]
      String result = build.result
      if ((build.building && build.building != 'false') || (build.running && build.running != 'false')) {
        return new TaskResult(ExecutionStatus.RUNNING, [buildInfo: build])
      }

      if (build?.artifacts) {
        build.artifacts = buildArtifactFilter.filterArtifacts(build.artifacts as List<Map>)
      }

      outputs.buildInfo = build

      if (statusMap.containsKey(result)) {
        ExecutionStatus status = statusMap[result]
        Map<String, Object> properties = [:]
        if (stage.context.propertyFile) {
          properties = buildService.getPropertyFile(buildNumber, stage.context.propertyFile, master, job)
          if (properties.size() == 0 && result == 'SUCCESS') {
            throw new IllegalStateException("expected properties file ${stage.context.propertyFile} but one was not found or was empty")
          }
          outputs << properties
          outputs.propertyFileContents = properties
        }
        if (result == 'UNSTABLE' && stage.context.markUnstableAsSuccessful) {
          status = ExecutionStatus.SUCCEEDED
        }
        return new TaskResult(status, outputs, outputs)
      } else {
        return new TaskResult(ExecutionStatus.RUNNING, [buildInfo: build])
      }
    } catch (RetrofitError e) {
      if ([503, 500, 404].contains(e.response?.status)) {
        log.warn("Http ${e.response.status} received from `igor`, retrying...")
        return new TaskResult(ExecutionStatus.RUNNING)
      }

      throw e
    }
  }
}
