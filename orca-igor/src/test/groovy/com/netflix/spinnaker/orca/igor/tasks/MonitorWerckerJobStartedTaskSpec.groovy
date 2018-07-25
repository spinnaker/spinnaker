/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.mock.env.MockEnvironment
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class MonitorWerckerJobStartedTaskSpec extends Specification {
  def environment = new MockEnvironment()

  @Subject
  MonitorWerckerJobStartedTask task = new MonitorWerckerJobStartedTask();

  @Shared
  def pipeline = Execution.newPipeline("orca")

  def "should return running #expectedExecutionStatus if #result is not_built or #buildNumber missing"() {
    given:
    def stage = new Stage(pipeline, "wercker", [master: "builds", job: "orca", queuedBuild: 4])

    and:
    task.buildService = Stub(BuildService) {
      getBuild(stage.context.queuedBuild, stage.context.master, stage.context.job) >> [result: result, number: buildNumber]
    }

    expect:
    task.execute(stage).status == expectedExecutionStatus

    where:
    result      | buildNumber | expectedExecutionStatus
    "not_built" | 4           | ExecutionStatus.RUNNING
    "success"   | null        | ExecutionStatus.RUNNING
    "success"   | 4           | ExecutionStatus.SUCCEEDED
  }

}
