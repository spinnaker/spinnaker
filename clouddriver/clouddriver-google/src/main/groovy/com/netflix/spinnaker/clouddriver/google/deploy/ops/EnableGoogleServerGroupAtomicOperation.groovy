/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "enableServerGroup": { "serverGroupName": "myapp-dev-v000", "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
 */
class EnableGoogleServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {
  final String phaseName = "ENABLE_SERVER_GROUP"

  EnableGoogleServerGroupAtomicOperation(EnableDisableGoogleServerGroupDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    false
  }
}
