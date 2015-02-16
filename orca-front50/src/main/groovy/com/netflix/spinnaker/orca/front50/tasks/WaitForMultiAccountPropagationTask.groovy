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

package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.pipeline.CreateApplicationStage
import com.netflix.spinnaker.orca.front50.pipeline.DeleteApplicationStage
import com.netflix.spinnaker.orca.front50.pipeline.UpdateApplicationStage
import com.netflix.spinnaker.orca.front50.pipeline.UpsertApplicationStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
class WaitForMultiAccountPropagationTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 35000

  @Autowired
  Front50Service front50Service

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def application = stage.context.application as Map<String, String>
    def applicationName = application.name as String

    def globalAccounts = front50Service.credentials.findAll { it.global }*.name
    def targetAccount = stage.context.account as String
    def allAccounts = [targetAccount] + globalAccounts
    def status = ExecutionStatus.SUCCEEDED

    def isCreate = stage.execution.stages.find {
      it.type == CreateApplicationStage.MAYO_CONFIG_TYPE
    } != null
    def isUpdate = stage.execution.stages.find {
      it.type == UpdateApplicationStage.MAYO_CONFIG_TYPE
    } != null
    def isDelete = stage.execution.stages.find {
      it.type == DeleteApplicationStage.MAYO_CONFIG_TYPE
    } != null
    def isUpsert = stage.execution.stages.find {
      it.type == UpsertApplicationStage.MAYO_CONFIG_TYPE
    } != null

    if (isCreate || isUpdate || isUpsert) {
      allAccounts.each {
        def applicationInAccount = fetchApplication(it, applicationName)
        if (!applicationInAccount) {
          // application does not exist
          status = ExecutionStatus.RUNNING
        } else if (applicationInAccount.listAccounts() && !applicationInAccount.listAccounts().contains(targetAccount)) {
          // application exists but is not yet associated with the target account (global registry)
          status = ExecutionStatus.RUNNING
        }
      }
    } else if (isDelete) {
      allAccounts.each {
        def applicationInAccount = fetchApplication(it, applicationName)
        if (applicationInAccount) {
          // application still exists
          status = ExecutionStatus.RUNNING

          if (applicationInAccount.listAccounts() && !applicationInAccount.accounts.contains(targetAccount)) {
            // application exists but it is no longer associated with the specific target account (global registry)
            status = ExecutionStatus.SUCCEEDED
          }
        }
      }
    }

    return new DefaultTaskResult(status)
  }

  Application fetchApplication(String accountName, String applicationName) {
    try {
      return front50Service.get(accountName, applicationName)
    } catch (RetrofitError e) {
      if (e.response.status == 404) {
        return null
      }

      throw e
    }
  }
}
