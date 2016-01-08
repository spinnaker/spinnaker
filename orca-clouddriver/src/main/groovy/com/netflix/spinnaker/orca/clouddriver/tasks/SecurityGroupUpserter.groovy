/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage

interface SecurityGroupUpserter {
  public static final String OPERATION = "upsertSecurityGroup"

  /**
   * @return a two item list.
   *
   * First item is a list of operation descriptors. Each operation should be a single entry map keyed by the operation name,
   * with the operation map as the value. (A list of maps of maps? We must go deeper...)
   *
   * Second item is a Map<String, Object> to be inserted into the stage's output map.
   */
  def getOperationsAndExtraOutput(Stage stage)

  /**
   * @return true when, according to the underlying cloud provider, the security group has been updated to match the
   * specified security group.
   */
  boolean isSecurityGroupUpserted(MortService.SecurityGroup upsertedSecurityGroup, Stage stage)

  /**
   * @return The cloud provider type that this object supports.
   */
  String getCloudProvider()
}
