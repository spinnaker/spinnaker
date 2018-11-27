/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.*;

import javax.annotation.Nullable;

@Data
public class Process {
  private String guid;
  private int instances;
  private int memoryInMb;
  private int diskInMb;

  @Nullable
  private HealthCheck healthCheck;

  @Data
  public static class HealthCheck {
    @Nullable
    private String type;

    @Nullable
    private HealthCheckData data;
  }

  @Data
  public static class HealthCheckData {

    @Nullable
    private String endpoint;
  }
}
