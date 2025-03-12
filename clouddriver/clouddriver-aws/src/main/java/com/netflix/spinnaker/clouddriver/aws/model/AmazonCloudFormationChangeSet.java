/*
 * Copyright (c) 2019 Adevinta.
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
package com.netflix.spinnaker.clouddriver.aws.model;

import com.amazonaws.services.cloudformation.model.Change;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AmazonCloudFormationChangeSet {

  private String name;
  private String status;
  private String statusReason;
  private List<Change> changes;

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getStatusReason() {
    return statusReason;
  }

  public List<Change> getChanges() {
    return changes;
  }
}
