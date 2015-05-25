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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.ModifyAsgLaunchConfigurationStage
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import spock.lang.Specification
import spock.lang.Subject

class ModifyAsgLaunchConfigurationTaskSpec extends Specification {

  KatoService katoService = Mock(KatoService)
  @Subject ModifyAsgLaunchConfigurationTask task = new ModifyAsgLaunchConfigurationTask(kato: katoService)
  void 'should populate deploy.server.groups to enable force cache refresh'() {
    setup:
    def taskConfig = [
      credentials: 'test',
      region: region,
      asgName: asgName
    ]
    def stage = new OrchestrationStage(new Orchestration(), ModifyAsgLaunchConfigurationStage.MAYO_CONFIG_TYPE, taskConfig)

    when:
    def result = task.execute(stage)

    then:
    1 * katoService.requestOperations(_) >> rx.Observable.just(new TaskId('blerg'))
    result.stageOutputs.'deploy.server.groups' == [(region): [asgName]]

    where:
    region = 'us-east-1'
    asgName = 'myasg-v001'
  }
}
