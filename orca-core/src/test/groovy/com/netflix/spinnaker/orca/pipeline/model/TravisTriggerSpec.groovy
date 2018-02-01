/*
 * Copyright 2018 Netflix, Inc.
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

class TravisTriggerSpec extends AbstractTriggerSpec<TravisTrigger> {
  @Override
  protected Class<TravisTrigger> getType() {
    return TravisTrigger
  }

  @Override
  protected String getTriggerJson() {
    return """{
  "account": null,
  "branch": null,
  "buildInfo": {
    "artifacts": [
      {
        "displayPath": "metrics_0.0.245.2685931_amd64.deb",
        "fileName": "metrics_0.0.245.2685931_amd64.deb",
        "name": "metrics",
        "reference": "metrics_0.0.245.2685931_amd64.deb",
        "relativePath": "metrics_0.0.245.2685931_amd64.deb",
        "type": "deb",
        "version": "0.0.245.2685931"
      }
    ],
    "building": false,
    "duration": 72,
    "name": "spt-infrastructure/metrics",
    "number": 245,
    "result": "SUCCESS",
    "scm": [
      {
        "branch": "master",
        "committer": "Jørgen Jervidalo",
        "compareUrl": "https://github.schibsted.io/spt-infrastructure/metrics/compare/ba3d6c6eb3c6...c8c8199027ae",
        "message": "Add logging if error while creating metrics. Some cleanup.",
        "name": "master",
        "sha1": "c8c8199027ae53e5a31503504d74329cfb477f14",
        "timestamp": "2018-01-25T12:00:45Z"
      }
    ],
    "timestamp": "1516881731000",
    "url": "https://travis.schibsted.io/spt-infrastructure/metrics/builds/2685930"
  },
  "buildNumber": 245,
  "constraints": null,
  "cronExpression": null,
  "digest": null,
  "enabled": true,
  "expectedArtifactIds": null,
  "hash": null,
  "id": null,
  "job": "spt-infrastructure/metrics/master",
  "lastSuccessfulExecution": null,
  "master": "travis-schibsted",
  "parameters": {},
  "project": null,
  "propertyFile": null,
  "pubsubSystem": null,
  "repository": null,
  "runAsUser": "spt-delivery@schibsted.com",
  "secret": null,
  "slug": null,
  "source": "github",
  "subscriptionName": null,
  "tag": null,
  "type": "travis",
  "user": "spt-delivery@schibsted.com"
}
"""
  }
}
