/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks


import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class StartGoogleCloudBuildTaskSpec extends Specification {
  def ACCOUNT = "my-account"
  def BUILD = new HashMap<String, Object>()

  Execution execution = Mock(Execution)
  IgorService igorService = Mock(IgorService)

  @Subject
  StartGoogleCloudBuildTask task = new StartGoogleCloudBuildTask(igorService)

  def "starts a build"() {
    given:

    when:
    def stage = new Stage(execution, "googleCloudBuild", [account: ACCOUNT, buildDefinition: BUILD])
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.createGoogleCloudBuild(ACCOUNT, BUILD) >> GoogleCloudBuild.builder()
      .id("98edf783-162c-4047-9721-beca8bd2c275")
      .build();
  }
}
