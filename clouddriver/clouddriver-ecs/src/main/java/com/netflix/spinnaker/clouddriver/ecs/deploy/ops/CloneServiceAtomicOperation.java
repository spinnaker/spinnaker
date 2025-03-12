/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class CloneServiceAtomicOperation implements AtomicOperation<Void> {

  CloneServiceDescription description;

  public CloneServiceAtomicOperation(CloneServiceDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }
}
