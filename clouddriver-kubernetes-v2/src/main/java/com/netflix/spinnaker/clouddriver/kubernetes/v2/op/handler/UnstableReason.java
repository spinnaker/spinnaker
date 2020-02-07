/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import lombok.Getter;

enum UnstableReason {
  AVAILABLE_REPLICAS("Waiting for all replicas to be available"),
  FULLY_LABELED_REPLICAS("Waiting for all replicas to be fully-labeled"),
  OLD_REPLICAS("Waiting for old replicas to finish termination"),
  READY_REPLICAS("Waiting for all replicas to be ready"),
  UPDATED_REPLICAS("Waiting for all replicas to be updated");

  @Getter private final String message;

  UnstableReason(String message) {
    this.message = message;
  }
}
