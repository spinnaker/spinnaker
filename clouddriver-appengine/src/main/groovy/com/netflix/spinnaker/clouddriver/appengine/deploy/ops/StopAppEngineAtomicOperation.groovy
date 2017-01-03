/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.StartStopAppEngineDescription

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "stopServerGroup": { "serverGroupName": "app-stack-detail-v000", "credentials": "my-appengine-account" }} ]' localhost:7002/appengine/ops
 */
class StopAppEngineAtomicOperation extends AbstractStartStopAppEngineAtomicOperation {
  StopAppEngineAtomicOperation(StartStopAppEngineDescription description) {
    super(description)
  }

  boolean start = false
  String basePhase = "STOP_SERVER_GROUP"
}
