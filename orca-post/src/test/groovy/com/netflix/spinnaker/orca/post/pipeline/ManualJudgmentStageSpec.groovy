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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.post.PostService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Unroll

import static ManualJudgmentStage.*

class ManualJudgmentStageSpec extends Specification {
  @Unroll
  void "should return execution status based on judgmentStatus"() {
    given:
    def task = new WaitForManualJudgmentTask()

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", context))

    then:
    result.status == expectedStatus
    result.stageOutputs.isEmpty()

    where:
    context                      || expectedStatus
    [:]                          || ExecutionStatus.RUNNING
    [judgmentStatus: "continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "Continue"] || ExecutionStatus.SUCCEEDED
    [judgmentStatus: "stop"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "STOP"]     || ExecutionStatus.TERMINAL
    [judgmentStatus: "unknown"]  || ExecutionStatus.RUNNING
  }

  void "should only send notifications for supported types"() {
    given:
    def task = new WaitForManualJudgmentTask(postService: Mock(PostService))

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", [notifications: [
      new Notification(type: "email", address: "test@netflix.com"),
      new Notification(type: "hipchat", address: "Hipchat Channel"),
      new Notification(type: "sms", address: "11122223333"),
      new Notification(type: "unknown", address: "unknown")
    ]]))

    then:
    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.notifications.findAll { it.lastNotified }*.type == ["email", "hipchat", "sms"]
  }

  @Unroll
  void "should notify if `notifyEveryMs` duration has been exceeded"() {
    expect:
    notification.shouldNotify(now) == shouldNotify

    where:
    notification                                                      | now             || shouldNotify
    new Notification()                                                | new Date()      || true
    new Notification(lastNotified: new Date(1))                       | new Date()      || false
    new Notification(lastNotified: new Date(1), notifyEveryMs: 60000) | new Date(60001) || true
  }

  void "should update `lastNotified` whenever a notification is sent"() {
    given:
    def postService = Mock(PostService)
    def notification = new Notification(type: "sms", address: "111-222-3333")

    def stage = new PipelineStage(new Pipeline(), "")
    stage.execution.id = "ID"
    stage.execution.application = "APPLICATION"

    when:
    notification.notify(postService, stage)

    then:
    notification.lastNotified != null

    1 * postService.create({ PostService.Notification n ->
      assert n.notificationType == PostService.Notification.Type.SMS
      assert n.to == ["111-222-3333"]
      assert n.templateGroup == "manualJudgment"
      assert n.severity == PostService.Notification.Severity.HIGH

      assert n.source.executionId == "ID"
      assert n.source.executionType == "pipeline"
      assert n.source.application == "APPLICATION"
      true
    } as PostService.Notification)
    0 * _
  }
}
