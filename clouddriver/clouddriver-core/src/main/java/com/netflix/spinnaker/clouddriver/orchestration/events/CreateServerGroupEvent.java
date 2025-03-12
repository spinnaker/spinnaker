/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration.events;

public class CreateServerGroupEvent implements OperationEvent {
  private final OperationEvent.Type type = OperationEvent.Type.SERVER_GROUP;
  private final OperationEvent.Action action = OperationEvent.Action.CREATE;

  private final String cloudProvider;

  private final String accountId;
  private final String region;
  private final String name;

  public CreateServerGroupEvent(
      String cloudProvider, String accountId, String region, String name) {
    this.cloudProvider = cloudProvider;
    this.accountId = accountId;
    this.region = region;
    this.name = name;
  }

  @Override
  public OperationEvent.Type getType() {
    return type;
  }

  @Override
  public OperationEvent.Action getAction() {
    return action;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getRegion() {
    return region;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "CreateServerGroupEvent{"
        + "type="
        + type
        + ", action="
        + action
        + ", cloudProvider='"
        + cloudProvider
        + '\''
        + ", accountId='"
        + accountId
        + '\''
        + ", region='"
        + region
        + '\''
        + ", name='"
        + name
        + '\''
        + '}';
  }
}
