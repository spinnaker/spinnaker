/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class VerifyApplicationHasNoDependenciesTaskSpec extends Specification {
  @Shared
  def config = [application: ["name": "application"]]
  def pipeline = pipeline {
    stage {
      type = "VerifyApplication"
      context = config
    }
  }

  CloudDriverService cloudDriverService = Mock()

  @Unroll
  void "should be TERMINAL when application has clusters or security groups"() {
    given:
    def fixedSecurityGroups = securityGroups
    def fixedClusters = clusters
    def task = new VerifyApplicationHasNoDependenciesTask() {

      @Override
      protected List<Map> getMortResults(String applicationName, String type) {
        return fixedSecurityGroups
      }
    }
    task.objectMapper = new ObjectMapper()
    task.cloudDriverService = cloudDriverService
    cloudDriverService.getApplication(_) >> [clusters: fixedClusters]

    and:

    def stage = pipeline.stages.first()
    stage.tasks = [new TaskExecutionImpl(name: "T1"), new TaskExecutionImpl(name: "T2")]

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == executionStatus

    where:
    clusters        | securityGroups                             || executionStatus
    []              | []                                         || ExecutionStatus.SUCCEEDED
    [['a cluster']] | []                                         || ExecutionStatus.TERMINAL
    []              | [["application": config.application.name]] || ExecutionStatus.TERMINAL
    [['a cluster']] | [["application": config.application.name]] || ExecutionStatus.TERMINAL
  }
}
