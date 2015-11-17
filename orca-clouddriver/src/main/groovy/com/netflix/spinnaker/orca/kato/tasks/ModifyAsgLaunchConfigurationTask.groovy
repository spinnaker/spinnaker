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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Slf4j
class ModifyAsgLaunchConfigurationTask implements Task, DeploymentDetailsAware {
  @Autowired
  KatoService kato

  @Value('${default.bake.account:default}')
  String defaultBakeAccount

  @Override
  TaskResult execute(Stage stage) {
    def operationConfig = new HashMap(stage.context)
    operationConfig.amiName = getImage(stage)

    def ops = []
    if (stage.context.credentials != defaultBakeAccount) {
      ops << [allowLaunchDescription: convertAllowLaunch(stage.context.credentials, defaultBakeAccount,
                                                         stage.context.region, operationConfig.amiName)]
      log.info("Generated `allowLaunchDescription` (allowLaunchDescription: ${ops})")
    }

    ops << [modifyAsgLaunchConfigurationDescription: operationConfig]

    def taskId = kato.requestOperations(CloudProviderAware.DEFAULT_CLOUD_PROVIDER, ops)
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"                        : "modifyasglaunchconfiguration",
      "modifyasglaunchconfiguration.account.name": stage.context.credentials,
      "modifyasglaunchconfiguration.region"      : stage.context.region,
      "kato.last.task.id"                        : taskId,
      "kato.task.id"                             : taskId, // TODO retire this.
      "deploy.server.groups"                     : [(stage.context.region): [stage.context.asgName]]
    ])
  }

  private String getImage(Stage stage) {
    String amiName = stage.context.amiName
    String targetRegion = stage.context.region
    withImageFromPrecedingStage(stage, targetRegion) {
      amiName = amiName ?: it.amiName
    }

    withImageFromDeploymentDetails(stage, targetRegion) {
      amiName = amiName ?: it.amiName
    }
    return amiName
  }

  private static Map convertAllowLaunch(String targetAccount, String sourceAccount, String region, String ami) {
    [account: targetAccount, credentials: sourceAccount, region: region, amiName: ami]
  }
}
