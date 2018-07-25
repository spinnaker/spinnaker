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
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorWerckerJobStartedTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = 10000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Override
  TaskResult execute(@Nonnull final Stage stage) {
    String master = stage.context.master
    String job = stage.context.job
    Integer buildNumber = Integer.valueOf(stage.context.queuedBuild)

    try {
      Map<String, Object> build = buildService.getBuild(buildNumber, master, job)
      Map outputs = [:]
      if ("not_built".equals(build?.result) || build?.number == null) {
        //The build has not yet started, so the job started monitoring task needs to be re-run
        return new TaskResult(ExecutionStatus.RUNNING)
      } else {
        //The build has started, so the job started monitoring task is completed
        return new TaskResult(ExecutionStatus.SUCCEEDED, [buildNumber: build.number])
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
