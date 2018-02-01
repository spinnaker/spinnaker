/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

class PipelineTriggerSpec extends AbstractTriggerSpec {

  @Override
  protected Class getType() {
    PipelineTrigger
  }

  @Override
  String getTriggerJson() {
    '''
{
  "parentExecution": {
    "type": "PIPELINE",
    "id": "848449c9-b152-4cd6-b22c-bd88d619df77",
    "application": "fletch_test",
    "name": "Chained 1",
    "buildTime": 1513102084081,
    "canceled": false,
    "canceledBy": null,
    "cancellationReason": null,
    "limitConcurrent": true,
    "keepWaitingPipelines": false,
    "stages": [
      {
        "id": "ea50d669-2a0f-4801-9f89-779a135e5693",
        "refId": "1",
        "type": "wait",
        "name": "Wait",
        "startTime": 1513102084163,
        "endTime": 1513102099340,
        "status": "SUCCEEDED",
        "context": {
          "waitTime": 1,
          "stageDetails": {
            "name": "Wait",
            "type": "wait",
            "startTime": 1513102084163,
            "isSynthetic": false,
            "endTime": 1513102099340
          },
          "waitTaskState": {}
        },
        "outputs": {},
        "tasks": [
          {
            "id": "1",
            "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask",
            "name": "wait",
            "startTime": 1513102084205,
            "endTime": 1513102099313,
            "status": "SUCCEEDED",
            "stageStart": true,
            "stageEnd": true,
            "loopStart": false,
            "loopEnd": false
          }
        ],
        "syntheticStageOwner": null,
        "parentStageId": null,
        "requisiteStageRefIds": [],
        "scheduledTime": null,
        "lastModified": null
      }
    ],
    "startTime": 1513102084130,
    "endTime": 1513102099392,
    "status": "SUCCEEDED",
    "authentication": {
      "user": "rfletcher@netflix.com",
      "allowedAccounts": [
        "titusmcestaging",
        "titusprodmce",
        "mceprod",
        "persistence_prod",
        "test",
        "mcetest",
        "titusdevint",
        "cpl",
        "dataeng_test",
        "mgmt",
        "prod",
        "dmz_test",
        "sitc_test",
        "titustestvpc",
        "lab_automation_prod",
        "sitc_prod",
        "seg_test",
        "iepprod",
        "lab_automation_test",
        "ieptest",
        "mgmttest",
        "itopsdev",
        "dmz",
        "titusprodvpc",
        "testregistry",
        "dataeng",
        "persistence_test",
        "prodregistry",
        "titusdevvpc",
        "itopsprod",
        "titustestmce"
      ]
    },
    "paused": null,
    "executionEngine": "v3",
    "origin": "deck",
    "trigger": {
      "type": "manual",
      "user": "rfletcher@netflix.com",
      "parameters": {},
      "notifications": []
    },
    "description": null,
    "pipelineConfigId": "241a8418-8649-4f61-bbd1-128bedaef658",
    "notifications": [],
    "initialConfig": {}
  },
  "parentPipelineId": "848449c9-b152-4cd6-b22c-bd88d619df77",
  "isPipeline": true,
  "parentStatus": "SUCCEEDED",
  "type": "pipeline",
  "user": "rfletcher@netflix.com",
  "parentPipelineName": "Chained 1",
  "parameters": {},
  "parentPipelineApplication": "fletch_test"
}
'''
  }
}
