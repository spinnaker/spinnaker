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



package com.netflix.spinnaker.orca.kato.tasks

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.UpsertSecurityGroupOperation
import com.netflix.spinnaker.orca.mort.MortService
import org.springframework.beans.factory.annotation.Autowired
import retrofit.client.Response

class UpsertSecurityGroupTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(TaskContext context) {
    def upsertSecurityGroupOperation = convert(context)
    def currentSecurityGroupResponse = mortService.getSecurityGroup(upsertSecurityGroupOperation.credentials, 'aws', upsertSecurityGroupOperation.name, upsertSecurityGroupOperation.region)

    String currentValue = parseCurrentValue(currentSecurityGroupResponse)

    def taskId = kato.requestOperations([[upsertSecurityGroupDescription: upsertSecurityGroupOperation]])
      .toBlocking()
      .first()

    Map outputs = [
      "kato.last.task.id"   : taskId,
      "kato.task.id"        : taskId, // TODO retire this.
      "upsert.account"      : upsertSecurityGroupOperation.credentials,
      "upsert.name"         : upsertSecurityGroupOperation.name,
      "upsert.region"       : upsertSecurityGroupOperation.region
    ]
    if (currentValue) {
      outputs.put("upsert.pre.response", currentValue)
    }

    new DefaultTaskResult(TaskResult.Status.SUCCEEDED, outputs)
  }

  UpsertSecurityGroupOperation convert(TaskContext context) {
    mapper.copy()
      .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
      .convertValue(context.getInputs("upsertSecurityGroup"), UpsertSecurityGroupOperation)
  }

  static String parseCurrentValue(Response response) {
    if (response.status != 200) {
      return null
    } else {
      return response.body.in().text ?: null
    }
  }
}
