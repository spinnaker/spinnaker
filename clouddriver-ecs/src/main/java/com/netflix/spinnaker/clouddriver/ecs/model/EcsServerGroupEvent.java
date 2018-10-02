/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import lombok.Data;

import java.util.Date;

@Data
public class EcsServerGroupEvent {

  String message;
  Date createdAt;
  String id;
  EcsServerGroupEventStatus status;

  public EcsServerGroupEvent(String message, Date createdAt, String id, EcsServerGroupEventStatus status) {
    this.message = message;
    this.createdAt = createdAt;
    this.id = id;
    this.status = status;
  }

  public EcsServerGroupEvent() {
  }

}
