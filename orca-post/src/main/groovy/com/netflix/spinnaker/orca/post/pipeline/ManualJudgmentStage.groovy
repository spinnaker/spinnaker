/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.post.pipeline

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.post.PostService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class ManualJudgmentStage extends LinearStage {
  private static final String MAYO_CONFIG_NAME = "manualJudgment"

  ManualJudgmentStage() {
    super(MAYO_CONFIG_NAME)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [buildStep(stage, "waitForJudgment", WaitForManualJudgmentTask)]
  }

  @Slf4j
  @Component
  @VisibleForTesting
  public static class WaitForManualJudgmentTask implements RetryableTask {
    long backoffPeriod = 1000
    long timeout = TimeUnit.HOURS.toMillis(120)

    @Autowired(required = false)
    PostService postService

    @Override
    TaskResult execute(Stage stage) {
      def stageData = stage.mapTo(StageData)
      switch (stageData.judgmentStatus.toLowerCase()) {
        case "continue":
          return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
        case "stop":
          return new DefaultTaskResult(ExecutionStatus.TERMINAL)
      }

      def outputs = [:]
      if (postService) {
        outputs = [notifications: stageData.notifications]
        stageData.notifications.findAll { it.shouldNotify() }.each {
          try {
            it.notify(postService, stage)
          } catch (Exception e) {
            log.error("Unable to send notification (executionId: ${stage.execution.id}, address: ${it.address}, type: ${it.type})", e)
          }
        }
      }

      return new DefaultTaskResult(ExecutionStatus.RUNNING, outputs)
    }
  }

  static class StageData {
    String judgmentStatus = ""
    List<Notification> notifications = []
  }

  static class Notification {
    String address
    String type

    Date lastNotified
    Long notifyEveryMs = -1

    boolean shouldNotify(Date now = new Date()) {
      if (!lastNotified?.time) {
        return true
      }

      if (notifyEveryMs <= 0) {
        return false
      }

      return new Date(lastNotified.time + notifyEveryMs) <= now
    }

    void notify(PostService postService, Stage stage) {
      postService.create(new PostService.Notification(
        notificationType: PostService.Notification.Type.valueOf(type.toUpperCase()),
        to: [address],
        templateGroup: MAYO_CONFIG_NAME,
        severity: PostService.Notification.Severity.HIGH,
        source: new PostService.Notification.Source(
          executionType: stage.execution.class.simpleName.toLowerCase(),
          executionId: stage.execution.id,
          application: stage.execution.application
        ),
        additionalContext: [
            instructions: stage.context.instructions ?: ""
        ]
      ))
      lastNotified = new Date()
    }
  }
}
