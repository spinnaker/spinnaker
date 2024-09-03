/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence;

/** Requirements for information from a read replica */
public enum ReadReplicaRequirement {
  /**
   * No requirement. It's OK if the desired information is not up to date, nor even present in a
   * read replica.
   */
  NONE,

  /** Information from a read replica must be up to date. */
  UP_TO_DATE
}
