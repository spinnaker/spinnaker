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

package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.MortService;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface SecurityGroupUpserter {
  String OPERATION = "upsertSecurityGroup";

  /**
   * @return the OperationContext object that contains the cloud provider-specific list of
   *     operations as well as cloud provider-specific output key/value pairs to be included in the
   *     task's output.
   */
  OperationContext getOperationContext(StageExecution stage);

  /**
   * @return true when, according to the underlying cloud provider, the security group has been
   *     updated to match the specified security group.
   */
  boolean isSecurityGroupUpserted(
      MortService.SecurityGroup upsertedSecurityGroup, StageExecution stage);

  /** @return The cloud provider type that this object supports. */
  String getCloudProvider();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  class OperationContext {
    /**
     * The list of operations to send to Clouddriver. Each operation should be a single entry map
     * keyed by the operation name, with the operation map as the value. (A list of maps of maps? We
     * must go deeper...)
     */
    List<Map> operations;

    /** Each key/value pair in the returned map will be added to the task's output. */
    Map extraOutput;
  }
}
