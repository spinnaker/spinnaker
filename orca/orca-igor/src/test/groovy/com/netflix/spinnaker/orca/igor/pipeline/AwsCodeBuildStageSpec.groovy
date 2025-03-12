/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.MonitorAwsCodeBuildTask
import com.netflix.spinnaker.orca.igor.tasks.StartAwsCodeBuildTask
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class AwsCodeBuildStageSpec extends Specification {
  def ACCOUNT = "codebuild-account"
  def PROJECT_NAME = "test"

  def "should start a build"() {
    given:
    def awsCodeBuildStage = new AwsCodeBuildStage()

    def stage = stage {
      type = "awsCodeBuild"
      context = [
        account: ACCOUNT,
        projectName: PROJECT_NAME,
      ]
    }

    when:
    def tasks = awsCodeBuildStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == StartAwsCodeBuildTask
    }.size() == 1
  }

  def "should wait for completion"() {
    given:
    def awsCodeBuildStage = new AwsCodeBuildStage()

    def stage = stage {
      type = "awsCodeBuild"
      context = [
          account: ACCOUNT,
          projectName: PROJECT_NAME,
      ]
    }

    when:
    def tasks = awsCodeBuildStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == MonitorAwsCodeBuildTask
    }.size() == 1
  }
}
