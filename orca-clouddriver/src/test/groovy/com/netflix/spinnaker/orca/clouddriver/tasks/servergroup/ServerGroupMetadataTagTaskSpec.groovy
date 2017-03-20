/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ServerGroupMetadataTagTaskSpec extends Specification {

  KatoService katoService = Mock(KatoService)

  @Subject
  ServerGroupMetadataTagTask task = new ServerGroupMetadataTagTask(kato: katoService)

  List<Map> taggingOps = null

  void "should return with failed/continue status if tagging operation fails"() {
    when:
    def stage = new Stage<>(new Pipeline(), "whatever", [:])
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.FAILED_CONTINUE
    1 * katoService.requestOperations(_) >> { throw new RuntimeException("something went wrong") }
  }

  void "sends tag with all relevant metadata for each server group"() {
    given:
    mockTaggingOperation()
    List<Map> expectedTags = [
      [
        stageId: "x",
        executionType: "pipeline",
        description: "Deploy to us-east-1",
        pipelineConfigId: "config-id",
        application: "foo",
        executionId: "ex-id",
        user: "chris"
      ],
      [
        stageId: "x",
        executionType: "pipeline",
        description: "Deploy to us-east-1",
        pipelineConfigId: "config-id",
        application: "foo",
        executionId: "ex-id",
        user: "chris"
      ],
      [
        stageId: "x",
        executionType: "pipeline",
        description: "Deploy to us-east-1",
        pipelineConfigId: "config-id",
        application: "foo",
        executionId: "ex-id",
        user: "chris"
      ]
    ]

    when:
    def pipeline = new Pipeline(
      pipelineConfigId: "config-id",
      name: "Deploy to us-east-1",
      application: "foo",
      id: "ex-id",
      authentication: [ user: "chris" ]
    )
    def stage = new Stage<>(pipeline, "whatever", [
      "deploy.server.groups": [
        "us-east-1": ["foo-v001"],
        "us-west-1": ["foo-v001", "foo-v002"]
      ]
    ])
    stage.id = "x"
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    taggingOps == expectedTags
  }

  void "omits user if authentication not found"() {
    given:
    mockTaggingOperation()

    when:
    def stage = new Stage<>(new Pipeline(), "whatever", [
      "deploy.server.groups": [
        "us-east-1": ["foo-v001"],
      ]
    ])
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    taggingOps[0].user == null
  }

  void "includes description on tasks"() {
    given:
    mockTaggingOperation()

    when:
    def orchestration = new Orchestration(description: "some description")
    def stage = new Stage<>(orchestration, "zzz", [
      "deploy.server.groups": [
        "us-east-1": ["foo-v001"],
      ]
    ])
    task.execute(stage)

    then:
    taggingOps[0].description == "some description"
  }

  @Unroll
  void "prefers comments to reason from context and applies as 'comments' field"() {
    given:
    mockTaggingOperation()

    when:
    def stage = new Stage<>(new Pipeline(), "whatever", [
      "deploy.server.groups": [
        "us-east-1": ["foo-v001"],
      ],
      comments: comments,
      reason: reason
    ])
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    taggingOps[0].comments == expected

    where:
    comments | reason || expected
    null     | null   || null
    null     | "r"    || "r"
    "c"      | null   || "c"
    "c"      | "r"    || "c"
  }

  private void mockTaggingOperation() {
    1 * katoService.requestOperations({ ops ->
      taggingOps = ops.collect { it.upsertEntityTags.tags[0].value }
      true
    }) >> rx.Observable.just(new TaskId("eh"))
  }
}
