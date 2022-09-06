/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.config;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("executions")
public class ExecutionConfigurationProperties {
  /**
   * flag to enable/disable blocking of orchestration executions (ad-hoc operations). By default, it
   * is turned off, which means all ad-hoc executions are supported. When set to true, it will only
   * allow those operations that are defined in allowedOrchestrationExecutions set.
   */
  private boolean blockOrchestrationExecutions = false;

  /**
   * this is only applicable when blockOrchestrationExecutions: true. This defines a set of
   * orchestration executions that will be allowed to execute. Every orchestration execution not in
   * this set will be blocked.
   *
   * <p>Finer level of control over an allowed orchestration execution can be achieved by
   * configuring {@link OrchestrationExecution} accordingly.
   */
  private Set<OrchestrationExecution> allowedOrchestrationExecutions = Set.of();

  /**
   * helper method that returns an {@link Optional<OrchestrationExecution>} object if the
   * orchestration execution provided as an input is in the allowed orchestration executions set.
   *
   * @param orchestrationExecutionType orchestration type to be checked
   * @return {@link Optional<OrchestrationExecution> object
   */
  public Optional<OrchestrationExecution> getAllowedOrchestrationType(
      String orchestrationExecutionType) {
    return allowedOrchestrationExecutions.stream()
        .filter(type -> type.getName().equals(orchestrationExecutionType))
        .findAny();
  }

  /**
   * this contains metadata about an orchestration execution, in terms of what it is and who is
   * allowed to execute it.
   *
   * <p>Finer level of granularity can be achieved by configuring this class appropriately. At
   * present, it defines an orchestration execution, and whether all users or a subset of users are
   * allowed to execute that operation
   */
  @Data
  public static class OrchestrationExecution {
    /**
     * orchestration action
     *
     * <p>TODO: find a way to reconcile this with all the supported/registered {@link Task} types
     */
    private String name;

    /**
     * controls whether all users are permitted to perform the orchestration. NOTE: this is mutually
     * exclusive with the permittedUsers property. Permitted users is given priority over this one
     * in this property's setter. If there are any explicitly defined permitted users, then
     * allowAllUsers == false.
     */
    private boolean allowAllUsers = true;

    /**
     * define a set of permitted users who can perform the orchestration. NOTE: this is mutually
     * exclusive with the allowAllUsers property. Permitted users is given priority over
     * allowAllUsers in this property's setter. If there are any explicitly defined permitted users,
     * then allowAllUsers == false.
     */
    Set<String> permittedUsers = Set.of();

    public void setAllowAllUsers(boolean allowAllUsers) {
      if (!this.permittedUsers.isEmpty()) {
        this.allowAllUsers = false;
      } else {
        this.allowAllUsers = allowAllUsers;
      }
    }

    public void setPermittedUsers(Set<String> permittedUsers) {
      this.permittedUsers = permittedUsers;
      if (!permittedUsers.isEmpty()) {
        this.allowAllUsers = false;
      }
    }
  }
}
