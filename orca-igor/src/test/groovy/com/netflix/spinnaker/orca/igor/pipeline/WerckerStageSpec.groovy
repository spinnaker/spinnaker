/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.MonitorWerckerJobStartedTask
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WerckerStageSpec extends Specification {
  def "should use the Wercker specific job-started monitor"() {
    given:
    def werckerStage = new WerckerStage()

    def stage = stage {
      type = "wercker"
      context = [
        master      : "builds",
        job         : "orca",
        buildNumber : 4,
      ]
    }

    when:
    def tasks = werckerStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.name == 'waitForWerckerJobStart' && it.implementingClass == MonitorWerckerJobStartedTask
    }.size() == 1
  }
}
